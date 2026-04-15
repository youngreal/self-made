# Consistent Hashing — 직접 구현 & 벤치마크

"가상면접 사례로 배우는 대규모 시스템 설계 기초" 5장(안정 해시 설계)을 직접 구현하면서,
**모듈러 해싱**과 **안정해시의 차이점**을 수치화한 프로젝트.

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
    ├── VirtualNodeBenchmark.java  (가상 노드 개수에 따른 분포 균등성)
    └── CacheMissBenchmark.java    (Zipfian 트래픽 + 추가/제거 시나리오별 미스율)
```

## 핵심 아이디어

### 문제 — Modulo 해싱의 단점

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
- Ketama(libmemcached) 표준값이 **160**인 이유: 50~200 구간에서 개선폭이 완만해짐

### 3) Cache Miss Benchmark — 시나리오별 재배치/미스율/부하불균형

**Zipfian(s=1.0) 분포** 100,000 요청, 1000 고유 키, 상위 10개 키가 트래픽의 39%를 차지.
초기 3노드 + `ADD(+1)` 또는 `REMOVE(-1)` 이후 재측정.

| scenario        | router     | 재배치율 | 미스율  | 미스 건수 | 부하불균형 σ | 제거 노드   |
|-----------------|------------|---------:|--------:|----------:|-------------:|-------------|
| **ADD (+1)**        | Simple     |  74.30%  | 69.34%  |   69,337  |    8,089.5   | -           |
| **ADD (+1)**        | Consistent |  25.50%  | 22.97%  |   22,972  |    7,885.8   | -           |
| **REMOVE first**    | Simple     |  65.70%  | 67.55%  |   67,550  |    6,415.0   | server-1    |
| **REMOVE first**    | Consistent |  34.70%  | 31.75%  |   31,748  |   14,671.0   | server-1    |
| **REMOVE busiest**  | Simple     |  66.60%  | 76.97%  |   76,966  |    6,415.0   | server-3    |
| **REMOVE busiest**  | Consistent |  36.00%  | **44.00%** | **43,996** |    9,405.0   | server-3    |

**핵심 관찰 5가지**:

1. **ADD +1**: Consistent는 이론값 `k/n ≈ 25%` 근처(25.5%)에 정확히 수렴. Simple은 74% 이상 재배치.
2. **REMOVE first**: Simple은 `hash % 3 → hash % 2`로 거의 전부 섞임(66%). Consistent는 `k/n ≈ 33%` 근처(34.7%)로 제한.
3. **REMOVE busiest ≫ REMOVE first**: 최다 트래픽 노드를 잃으면 **인기 키가 쓸려나감** → 미스율 +10%p 이상 증폭. Consistent 31.75% → 44.00%.
4. **미스 "건수" 격차**: 10만 요청 기준 Simple 77K vs Consistent 44K. **DB 부하 배수** 차이는 1.75배.
5. **부하불균형 σ (Consistent REMOVE)**: 14,671로 가장 큼. 제거된 노드의 키가 **링 시계방향 인접 노드로 몰리기** 때문. 이게 consistent hashing이 완벽하지 않은 순간 — 장애 복구 시 **hot spot** 유발.

**실무 시사점**:
- 재배치율(이론 지표)만 보지 말고 **트래픽 가중 미스율**을 같이 측정해야 함
- 노드 **제거**가 추가보다 항상 더 아픔. 장애 시나리오를 벤치마크에 꼭 포함
- Consistent의 REMOVE 부하불균형 → **bounded-load consistent hashing** (Google 2017)이 나온 동기
- 운영에선 **단일 장애 = 인접 노드 2배 부하** 가정으로 용량 계획

## 실행 방법

```bash
./gradlew test              # 유닛 테스트 (T1~T4)
./gradlew runBenchmark      # 벤치마크 suite 실행
```

## 한계

- Hot key 문제는 consistent hashing으로 해결 안 됨. 저스틴 비버 트윗처럼 **한 키에 요청이 쏠리는 건** 별개 문제. 확장 기법:
  - Bounded-load consistent hashing (Google, 2017)
  - Replication + power of two choices
  - Client-side L1 캐시
