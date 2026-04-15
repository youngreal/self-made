# Consistent Hashing — 직접 구현 & 벤치마크

"가상면접 사례로 배우는 대규모 시스템 설계 기초" 5장(안정 해시 설계)을 직접 구현하면서,
**modulo 해싱이 왜 망하는지**와 **consistent hashing이 얼마나 개선하는지**를 수치로 증명한 프로젝트.

## 구조

```
consistenthashing/
├── hash/
│   ├── Node.java                  (interface)
│   ├── ServerNode.java            (물리 서버 노드)
│   ├── VirtualNode.java           (물리 노드 + replicaIndex, 링에 뿌릴 복제본)
│   ├── HashAlgorithm.java         (interface — DIP)
│   └── Md5Hash.java               (MD5 128bit 중 앞 8바이트를 long으로)
├── router/
│   ├── HashRouter.java            (interface)
│   ├── SimpleHashRouter.java      (하이 케이스: hash(key) % n)
│   └── ConsistentHashRouter.java  (TreeMap 기반 링 + tailMap 시계 방향 탐색)
└── benchmark/
    ├── BenchmarkRunner.java       (./gradlew runBenchmark 진입점)
    ├── RebalanceBenchmark.java    (노드 추가 시 키 이동률 측정)
    └── VirtualNodeBenchmark.java  (가상 노드 개수에 따른 분포 균등성)
```

## 핵심 아이디어

### 문제 — Modulo 해싱이 망하는 이유

```java
int index = (int) Long.remainderUnsigned(hash, nodes.size());
```

단순해 보이지만 **노드 1개 추가되면 거의 모든 키의 매핑이 바뀜**. 캐시 시스템이라면 전체 cache miss → DB 폭주 → 장애.

### 해결 — 해시 링 + 가상 노드

```java
long hash = hashAlgorithm.hash(businessKey);
SortedMap<Long, VirtualNode<T>> tail = ring.tailMap(hash);
long targetHash = tail.isEmpty() ? ring.firstKey() : tail.firstKey();
return ring.get(targetHash).getPhysicalNode();
```

- `TreeMap`으로 해시값을 정렬된 링으로 관리
- `tailMap(hash)`로 O(log N) 시계 방향 탐색
- `tail.isEmpty()`면 링을 한 바퀴 돌아 `firstKey()` — 원의 본질
- 각 물리 노드를 `virtualNodeCount`(기본 150)만큼 복제해 분포 균등화

## 실측 결과

### 1) Rebalance Benchmark — 노드 추가 시 이동률

초기 3노드 + 키 100,000개로 측정.

| node change | Simple (modulo) | Consistent (virtual=150) | theory k/n |
|-------------|----------------:|-------------------------:|-----------:|
| 3 → 4       | **75.05%**      | **26.44%**               | 25.00%     |
| 3 → 5       | 79.93%          | 41.40%                   | 40.00%     |
| 3 → 6       | 50.17%          | 50.40%                   | 50.00%     |

- Consistent hashing은 이론값 k/n에 **거의 정확히 수렴**
- Simple은 3→6 같은 배수 관계에서 우연히 낮게 나오지만, **예측 불가능**한 편차
- 10,000 → 100,000 키로 늘리면 Consistent의 이론값 수렴이 더 선명해짐

### 2) Virtual Node Effect — 분포 균등성

5 물리 노드 + 100,000 키. 이상값은 20,000개/노드.

| virtualNodes | 표준편차 | 최대편차 | max - min |
|-------------:|---------:|---------:|----------:|
| 1            | 7528.59  | 14146    | 20656     |
| 10           | 6071.87  | 9726     | 17626     |
| 50           | 2209.31  | 3337     | 5930      |
| 150          | 2469.94  | 3296     | 6447      |
| 500          | 1047.86  | 1742     | 3146      |
| 1000         | **414.42** | 512    | 961       |

- 가상노드 1개 → 1000개로 늘리면 **표준편차 약 18배 감소**
- 150 근처에서 50보다 약간 높은 건 단일 실행의 랜덤 노이즈 — 여러 번 평균이 필요
- Ketama(libmemcached) 표준값이 **160**인 이유: 50~200 구간에서 개선폭이 완만해짐 (diminishing returns)

## 실행 방법

```bash
./gradlew test              # 유닛 테스트 (T1~T4)
./gradlew runBenchmark      # 벤치마크 suite 실행
```

## 실무 맥락

- **쓰는 곳**: DynamoDB, Cassandra, Memcached(Ketama), Discord 메시지 라우팅, Envoy `ring_hash`
- **안 쓰는 곳**: **Redis Cluster** — hash slot 16384로 고정 샤딩. 운영 단순성이 유연성보다 우선인 선택.
- **한계**: Hot key 문제는 consistent hashing으로 해결 안 됨. 저스틴 비버 트윗처럼 **한 키에 요청이 쏠리는 건** 별개 문제. 확장 기법:
  - Bounded-load consistent hashing (Google, 2017)
  - Replication + power of two choices
  - Client-side L1 캐시

## 배운 것

- `TreeMap.tailMap`의 O(log N) 탐색 특성 (HashMap으로는 불가)
- `Long.remainderUnsigned`로 해시 음수 오버플로우 회피
- `Long.MIN_VALUE`의 `Math.abs` 함정 (양수 안 됨)
- Java 제네릭 `<T extends Node>`의 선언 위치 규칙
- 테스트가 "설계 문서"가 되는 패턴 — T3가 modulo 해싱의 치명적 단점을 코드로 증명
