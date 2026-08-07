#!/bin/bash
# ===============================================
# 薄弱点分析 API 集成测试脚本
# 版本: 4.1.2 + 4.1.3
# 用法: bash test-weak-points.sh
# ===============================================

BASE_URL="http://localhost:8080"
PYTHON_URL="http://localhost:8000"
PASS=0
FAIL=0

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass() { echo -e "${GREEN}[PASS]${NC} $1"; PASS=$((PASS+1)); }
fail() { echo -e "${RED}[FAIL]${NC} $1 - $2"; FAIL=$((FAIL+1)); }

# ==================== Step 1: 登录 ====================
echo "==============================================="
echo "  Step 1: 登录获取 Token"
echo "==============================================="

LOGIN_RESP=$(curl -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}')

TOKEN=$(echo "$LOGIN_RESP" | python -c "import sys,json;print(json.load(sys.stdin)['data']['token'])" 2>/dev/null)

if [ -n "$TOKEN" ]; then
  pass "登录成功, Token: ${TOKEN:0:30}..."
else
  fail "登录" "无法获取Token"
  echo "Response: $LOGIN_RESP"
  exit 1
fi

AUTH="Authorization: Bearer $TOKEN"

# ==================== Step 2: 插入测试数据 ====================
echo ""
echo "==============================================="
echo "  Step 2: 准备测试数据"
echo "==============================================="

mysql -u root -p1234 leapmind-voice -e "
DELETE FROM user_exercises WHERE user_id=1;
DELETE FROM user_weak_points WHERE user_id=1;
INSERT INTO user_weak_points (user_id, knowledge_point, subject, weakness_level, error_count, total_count, accuracy_rate, last_error_time, status) VALUES
(1, '分数加减法', '数学', 'HIGH', 8, 12, 33.33, NOW(), 'ACTIVE'),
(1, '方程求解', '数学', 'MEDIUM', 4, 10, 60.00, NOW(), 'ACTIVE'),
(1, '古诗鉴赏', '语文', 'LOW', 2, 8, 75.00, NOW(), 'ACTIVE'),
(1, '圆的面积', '数学', 'HIGH', 9, 15, 40.00, NOW(), 'ACTIVE'),
(1, '一般过去时', '英语', 'MEDIUM', 5, 10, 50.00, NOW(), 'ACTIVE'),
(1, '拼音拼读', '语文', 'LOW', 2, 10, 80.00, NOW(), 'RESOLVED');
" 2>/dev/null && pass "测试数据已插入(6条)" || fail "数据插入" "请检查MySQL连接"

# ==================== Step 3: WP-001 查询薄弱点列表 ====================
echo ""
echo "==============================================="
echo "  Step 3: WP-001 查询薄弱点列表"
echo "==============================================="

# 3a: 全量查询
RESP=$(curl -s "$BASE_URL/api/weak-points?userId=1" -H "$AUTH")
COUNT=$(echo "$RESP" | python -c "import sys,json;print(len(json.load(sys.stdin)['data']))" 2>/dev/null)
[ "$COUNT" = "6" ] && pass "WP-001a 全量查询: $COUNT 条" || fail "WP-001a 全量查询" "期望6条, 实际${COUNT}条"

# 检查排序: 第一条应该是 HIGH
FIRST_LEVEL=$(echo "$RESP" | python -c "import sys,json;print(json.load(sys.stdin)['data'][0]['weaknessLevel'])" 2>/dev/null)
[ "$FIRST_LEVEL" = "HIGH" ] && pass "WP-001b 排序验证: 首条为 $FIRST_LEVEL" || fail "WP-001b 排序验证" "期望HIGH, 实际${FIRST_LEVEL}"

# 3c: 按学科过滤
RESP=$(curl -s "$BASE_URL/api/weak-points?userId=1&subject=%E6%95%B0%E5%AD%A6" -H "$AUTH")
MATH_COUNT=$(echo "$RESP" | python -c "import sys,json;print(len(json.load(sys.stdin)['data']))" 2>/dev/null)
[ "$MATH_COUNT" = "3" ] && pass "WP-001c 学科过滤(数学): $MATH_COUNT 条" || fail "WP-001c 学科过滤" "期望3条, 实际${MATH_COUNT}条"

# 3d: 按状态过滤
RESP=$(curl -s "$BASE_URL/api/weak-points?userId=1&status=RESOLVED" -H "$AUTH")
RESOLVED_COUNT=$(echo "$RESP" | python -c "import sys,json;print(len(json.load(sys.stdin)['data']))" 2>/dev/null)
[ "$RESOLVED_COUNT" = "1" ] && pass "WP-001d 状态过滤(RESOLVED): $RESOLVED_COUNT 条" || fail "WP-001d 状态过滤" "期望1条, 实际${RESOLVED_COUNT}条"

# ==================== Step 4: WP-002 单用户查询 ====================
echo ""
echo "==============================================="
echo "  Step 4: WP-002 单用户查询"
echo "==============================================="

RESP=$(curl -s "$BASE_URL/api/weak-points/1" -H "$AUTH")
U_COUNT=$(echo "$RESP" | python -c "import sys,json;print(len(json.load(sys.stdin)['data']))" 2>/dev/null)
[ "$U_COUNT" = "6" ] && pass "WP-002 单用户查询: $U_COUNT 条" || fail "WP-002 单用户查询" "期望6条, 实际${U_COUNT}条"

# ==================== Step 5: WP-004 推荐练习 ====================
echo ""
echo "==============================================="
echo "  Step 5: WP-004 推荐练习"
echo "==============================================="

RESP=$(curl -s "$BASE_URL/api/exercises/recommend?userId=1&subject=%E6%95%B0%E5%AD%A6&count=3" -H "$AUTH")
REC_COUNT=$(echo "$RESP" | python -c "import sys,json;print(len(json.load(sys.stdin)['data']))" 2>/dev/null)
PRI1=$(echo "$RESP" | python -c "import sys,json;print(json.load(sys.stdin)['data'][0]['priority'])" 2>/dev/null)
FIRST_KP=$(echo "$RESP" | python -c "import sys,json;print(json.load(sys.stdin)['data'][0]['knowledgePoint'])" 2>/dev/null)

[ "$REC_COUNT" = "3" ] && pass "WP-004a 推荐数量: $REC_COUNT 条" || fail "WP-004a 推荐数量" "期望3条, 实际${REC_COUNT}条"
echo "  首条: $FIRST_KP (priority=$PRI1, 应为HIGH级薄弱点)"

# ==================== Step 6: WP-005 记录练习 ====================
echo ""
echo "==============================================="
echo "  Step 6: WP-005 记录练习结果"
echo "==============================================="

# 6a: 正常记录(答对)
RESP=$(curl -s -X POST "$BASE_URL/api/exercises/record" \
  -H "Content-Type: application/json" -H "$AUTH" \
  -d '{"userId":1,"exerciseId":"TEST_001","knowledgePoint":"分数加减法","subject":"数学","isCorrect":1}')
CODE=$(echo "$RESP" | python -c "import sys,json;print(json.load(sys.stdin)['code'])" 2>/dev/null)
[ "$CODE" = "200" ] && pass "WP-005a 记录答对练习" || fail "WP-005a 记录答对练习" "$RESP"

# 6b: 正常记录(答错)
RESP=$(curl -s -X POST "$BASE_URL/api/exercises/record" \
  -H "Content-Type: application/json" -H "$AUTH" \
  -d '{"userId":1,"exerciseId":"TEST_002","knowledgePoint":"圆的面积","subject":"数学","isCorrect":0}')
CODE=$(echo "$RESP" | python -c "import sys,json;print(json.load(sys.stdin)['code'])" 2>/dev/null)
[ "$CODE" = "200" ] && pass "WP-005b 记录答错练习" || fail "WP-005b 记录答错练习" "$RESP"

# 6c: 缺少必填字段(校验失败)
RESP=$(curl -s -X POST "$BASE_URL/api/exercises/record" \
  -H "Content-Type: application/json" -H "$AUTH" \
  -d '{}')
CODE=$(echo "$RESP" | python -c "import sys,json;print(json.load(sys.stdin)['code'])" 2>/dev/null)
[ "$CODE" = "400" ] && pass "WP-005c 校验失败(空body)" || fail "WP-005c 校验失败" "期望400, 实际${CODE}"

# ==================== Step 7: 去重验证 ====================
echo ""
echo "==============================================="
echo "  Step 7: 去重验证"
echo "==============================================="

RESP=$(curl -s "$BASE_URL/api/exercises/recommend?userId=1&subject=%E6%95%B0%E5%AD%A6&count=10" -H "$AUTH")
HAS_TEST001=$(echo "$RESP" | python -c "import sys,json;d=json.load(sys.stdin);ids=[i['exerciseId'] for i in d['data']];print('TEST_001' in ids)" 2>/dev/null)

if [ "$HAS_TEST001" = "False" ]; then
  pass "WP-007 去重生效: TEST_001(7天内已做)已被排除"
else
  fail "WP-007 去重失效" "TEST_001 应该被排除但仍在推荐列表中"
fi

# ==================== Step 8: WP-003 AI 分析 ====================
echo ""
echo "==============================================="
echo "  Step 8: WP-003 AI 综合分析"
echo "==============================================="

RESP=$(curl -s -X POST "$BASE_URL/api/weak-points/1/analysis" -H "$AUTH")
CODE=$(echo "$RESP" | python -c "import sys,json;print(json.load(sys.stdin)['code'])" 2>/dev/null)
HAS_ANALYSIS=$(echo "$RESP" | python -c "import sys,json;d=json.load(sys.stdin)['data'];print(1 if d.get('comprehensiveAnalysis') else 0)" 2>/dev/null)

[ "$CODE" = "200" ] && pass "WP-003a AI分析响应码: 200" || fail "WP-003a AI分析响应码" "期望200, 实际${CODE}"
[ "$HAS_ANALYSIS" = "1" ] && pass "WP-003b AI分析内容: 已生成(降级模式)" || fail "WP-003b AI分析内容" "综合分析为空"

# ==================== Step 9: Python 服务健康检查 ====================
echo ""
echo "==============================================="
echo "  Step 9: Python AI 服务检查"
echo "==============================================="

PY_HEALTH=$(curl -s -o /dev/null -w "%{http_code}" "$PYTHON_URL/health" 2>/dev/null)
if [ "$PY_HEALTH" = "200" ]; then
  pass "PY-001 Python 服务运行中"
  PY_RESP=$(curl -s -X POST "$PYTHON_URL/api/weak-points/analyze" \
    -H "Content-Type: application/json" \
    -d '{"userId":1,"weakPoints":[{"knowledgePoint":"分数加减法","subject":"数学","weaknessLevel":"HIGH","errorCount":8,"totalCount":12}],"recentExercises":[],"language":"zh"}')
  PY_STATUS=$(echo "$PY_RESP" | python -c "import sys,json;print(json.load(sys.stdin).get('status','error'))" 2>/dev/null)
  [ "$PY_STATUS" = "success" ] && pass "PY-002 AI分析成功" || echo -e "${YELLOW}[WARN]${NC} Python AI返回status=$PY_STATUS (可能未配置API Key)"
else
  echo -e "${YELLOW}[SKIP]${NC} Python 服务未启动, 跳过AI分析测试"
fi

# ==================== 结果汇总 ====================
echo ""
echo "==============================================="
echo "  测试结果汇总"
echo "==============================================="
echo -e "  ${GREEN}通过: $PASS${NC}"
echo -e "  ${RED}失败: $FAIL${NC}"
echo ""

if [ "$FAIL" -gt 0 ]; then
  exit 1
else
  echo -e "${GREEN}  全部测试通过!${NC}"
  exit 0
fi
