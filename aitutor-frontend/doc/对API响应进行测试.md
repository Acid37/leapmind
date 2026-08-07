# 一、接口模块介绍

## 1. 被测试接口概述

### 接口基本信息
- **接口端点**：`POST /api/voice-chat/ask`
- **接口功能**：用户提问并获取AI回答（语音问答系统的核心接口）
- **所在模块**：`src/features/chat/pptApi.js` 中的 `askQuestion()` 函数
- **接口用途**：在整个AI教育平台中，该接口负责处理用户在教学过程中的提问，后端调用OpenAI等AI服务生成回答，返回给前端进行后续的语音合成和虚拟人物展示

### 接口调用示例
```javascript
// 实际项目中的调用方式
export async function askQuestion(sessionId, question) {
  const res = await fetch(`${VOICE_API_BASE}/api/voice-chat/ask`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({ sessionId, question }),
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`);
  return await res.json();
}
```

### 接口输入参数
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `sessionId` | String | 是 | 会话ID，用于标识当前教学会话 |
| `question` | String | 是 | 用户提问的问题文本 |

### 接口响应格式
```json
{
  "answer": "AI生成的回答内容"
}
```

- **成功响应**：HTTP 200，返回包含 `answer` 字段的JSON对象
- **失败响应**：HTTP 非200状态码，可能抛出错误

### 接口在系统中的位置
该接口是语音问答流程的核心环节之一，典型调用链路如下：
1. 用户输入问题（文本或语音识别后的文本）
2. **调用 `/api/voice-chat/ask` 获取AI回答**（本文档测试的接口）
3. 调用 `/api/voice-chat/synthesize` 进行语音合成
4. 驱动3D虚拟人物播放回答并进行口型同步

### 为什么要测试这个接口？
1. **响应时间直接影响用户体验**：接口响应慢会导致用户等待时间长，影响交互流畅度
2. **并发处理能力**：教学场景下可能同时有多个用户提问，需要验证并发场景下的性能和正确性
3. **边界情况处理**：需要确保在各种延时、并发数等边界条件下，接口仍能正常工作

---

# 二、测试环境配置

1、在项目根目录按照测试工具：
```npm install --save-dev jest @jest/globals```
说明：
- --save-dev：这些工具只在开发时用，不会进入生产环境。
- jest：测试框架。
- @jest/globals：Jest 的全局 API。

2、在项目根目录创建一个新文件，命名为 jest.config.js（和 package.json 同一层）。
文件内容：
```
export default {  
    testEnvironment: 'node',  
    transform: {},  
    moduleNameMapper: {    '^@/(.*)$': '<rootDir>/src/$1',  }, 
    testMatch: ['**/*.test.js'],
};
```
说明：
testEnvironment: 'node'：在 Node.js 环境中运行（测试 API，不是浏览器）。
transform: {}：告诉 Jest 这是 ES 模块，不要转换。
moduleNameMapper：支持 @/ 路径别名（你的项目用了 @）。
testMatch：只运行 .test.js 结尾的测试文件。

3、在 package.json 中添加测试脚本
打开 package.json，找到 "scripts" 部分，添加一行：
```
"scripts": {
      "dev": "vite",  
      "build": "vite build",  
      "lint": "eslint .",  
      "preview": "vite preview",  
      "test": "node --experimental-vm-modules node_modules/jest/bin/jest.js"
},
```
说明：
"test"：运行测试的命令。
--experimental-vm-modules：让 Jest 支持 ES 模块。

运行```npm test```,出现“No tests found, exiting with code 1”即为步骤2、3配置成功。

4、新建一个测试看是否配置成功，新建tests文件夹，将测试文件都放在该文件夹下
新建pptApi.test.js，内容如下：
```
import { describe, test, expect } from '@jest/globals';

describe('pptApi', () => {
    test('应该能运一个简单的测试',()=>{
        expect(1+1).toBe(2);
    })
});
```
运行npm test，就能得到绿色的通过测试

5、Mock fetch 并编写首个响应时间测试（1秒场景）
在 tests/ 目录新建文件 apiTiming.test.js，写入：
```
import { describe, test, expect, beforeEach, afterEach, jest } from '@jest/globals';


async function askQuestionMock() {
  const res = await fetch('http://localhost:8080/api/voice-chat/ask', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sessionId: 'test-session', question: 'test-question' }),
  });
  return res.json();
}

describe('API响应时间 - 基础场景', () => {
  beforeEach(() => {
    jest.useRealTimers();     
    global.fetch = jest.fn();
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  test('延时约等于1000ms 时应成功返回，且耗时接近1000ms', async () => {

    fetch.mockImplementationOnce(() =>
      new Promise((resolve) => {
        setTimeout(() => {
          resolve({
            ok: true,
            json: async () => ({ answer: 'ok' }),
          });
        }, 1000);
      })
    );

    const t0 = performance.now();
    const data = await askQuestionMock();
    const elapsed = performance.now() - t0;

    expect(data.answer).toBe('ok');
    expect(elapsed).toBeGreaterThanOrEqual(1000);
    expect(elapsed).toBeLessThan(1200);
  });
});
```
这段代码是一个使用 **Jest 测试框架** 编写的 **API 响应时间测试用例**，目的是验证某个接口在特定延时下的返回是否符合预期。我们可以一步步拆解它的逻辑和写法：


### 一、整体结构：测试的“骨架”
代码主要由 3 部分组成：
1. 导入 Jest 测试所需的核心函数（`describe`、`test` 等）
2. 定义要测试的函数（`askQuestionMock`，模拟调用 API 的行为）
3. 用 `describe` 组织测试套件，包含具体的测试用例和前置/后置操作


### 二、逐行拆解：从“导入”到“测试逻辑”

#### 1. 导入依赖
```javascript
import { describe, test, expect, beforeEach, afterEach, jest } from '@jest/globals';
```
- 从 Jest 框架中导入必要的工具函数：
  - `describe`：用于分组测试用例（如“API响应时间 - 基础场景”这个分组）
  - `test`：定义单个测试用例（具体要验证的场景）
  - `expect`：断言工具（判断实际结果是否符合预期）
  - `beforeEach`/`afterEach`：测试前后的钩子函数（准备/清理测试环境）
  - `jest`：Jest 的核心对象（用于模拟、计时等操作）


#### 2. 定义被测试的函数：`askQuestionMock`
```javascript
async function askQuestionMock() {
  const res = await fetch('http://localhost:8080/api/voice-chat/ask', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sessionId: 'test-session', question: 'test-question' }),
  });
  return res.json();
}
```
- 这是一个**模拟调用 API 的函数**（实际项目中可能是真实的接口调用）：
  - 用 `async/await` 处理异步请求（因为 API 调用是异步的）
  - 调用 `fetch` 发送 POST 请求到 `http://localhost:8080/api/voice-chat/ask`
  - 请求体包含参数 `sessionId` 和 `question`（测试用的固定值）
  - 最后返回接口响应的 JSON 数据


#### 3. 测试套件：`describe` 分组
```javascript
describe('API响应时间 - 基础场景', () => { ... })
```
- 这是一个测试套件，名称是“API响应时间 - 基础场景”，里面包含了针对该场景的测试逻辑。


#### 4. 前置/后置操作：`beforeEach` 和 `afterEach`
```javascript
beforeEach(() => {
  jest.useRealTimers();     // 使用真实时间（不模拟时间流速）
  global.fetch = jest.fn(); // 用 Jest 模拟全局的 fetch 函数（避免真实调用 API）
});

afterEach(() => {
  jest.clearAllMocks(); // 清空所有模拟函数的调用记录（避免影响下一个测试用例）
});
```
- `beforeEach`：每个测试用例执行**前**会运行的代码：
  - 启用真实计时器（确保 `setTimeout` 按真实时间延迟）
  - 用 `jest.fn()` 替换全局的 `fetch` 函数（“模拟”API 调用，避免实际发送请求到服务器）
- `afterEach`：每个测试用例执行**后**会运行的代码：
  - 清空所有模拟函数的记录（比如 `fetch` 被调用了几次、传了什么参数，避免残留数据影响其他测试）


#### 5. 核心测试用例：`test` 函数
```javascript
test('延时约等于1000ms 时应成功返回，且耗时接近1000ms', async () => { ... })
```
这是具体的测试逻辑，验证“当 API 延时约 1000ms 时，是否能成功返回，且实际耗时符合预期”。

##### 步骤1：模拟 API 响应延时
```javascript
fetch.mockImplementationOnce(() =>
  new Promise((resolve) => {
    setTimeout(() => {
      resolve({
        ok: true,
        json: async () => ({ answer: 'ok' }),
      });
    }, 1000); // 模拟 API 1000ms 后返回成功响应
  })
);
```
- 用 `fetch.mockImplementationOnce` 定义“这次调用 `fetch` 时的行为”：
  - 返回一个 Promise，通过 `setTimeout` 模拟 1000ms 的延时
  - 延时后返回一个成功的响应（`ok: true`），且响应的 JSON 数据为 `{ answer: 'ok' }`


##### 步骤2：执行测试并计算耗时
```javascript
const t0 = performance.now(); // 记录开始时间
const data = await askQuestionMock(); // 调用被测试的函数（实际会触发上面模拟的 fetch）
const elapsed = performance.now() - t0; // 计算总耗时（结束时间 - 开始时间）
```


##### 步骤3：断言验证结果
```javascript
expect(data.answer).toBe('ok'); // 验证响应数据是否正确（answer 应为 'ok'）
expect(elapsed).toBeGreaterThanOrEqual(1000); // 耗时应≥1000ms（因为模拟了1000ms延时）
expect(elapsed).toBeLessThan(1200); // 耗时应<1200ms（允许轻微误差，避免环境波动导致失败）
```
- 用 `expect` 断言结果是否符合预期：
  - 接口返回的数据必须正确
  - 响应时间不能太短（至少要达到模拟的 1000ms）
  - 响应时间也不能太长（避免实际执行时因其他因素导致耗时过高）


### 三、总结：这段代码在做什么？
简单说，它通过 **Jest 模拟 API 调用**，测试当接口延时 1000ms 时：
1. 是否能返回正确的响应数据（`{ answer: 'ok' }`）
2. 实际耗时是否在合理范围内（1000ms 左右，允许小误差）

这样的测试可以验证接口的“响应时间稳定性”，确保在预期延时下功能正常。

这几个问题涉及到测试的核心思路、异步编程基础以及 Jest 的模拟机制，我们逐一拆解：


### 一、为什么这里用“模拟调用 API 的函数”？实际项目中如何用真实接口？
#### 1. 为什么要模拟 API 调用？
测试的核心目标是**验证“被测试代码的逻辑是否正确”**，而不是验证“外部依赖是否正常”。  
- 如果直接调用真实 API，测试结果会受外部因素影响（比如网络波动、服务器 downtime、接口返回随机数据等），导致测试不稳定（时而通过时而失败）。  
- 模拟 API 可以**精准控制返回结果**（比如固定返回成功/失败、固定延时），确保测试只关注“我们的代码如何处理这些结果”，而不是依赖外部接口的状态。  

比如这段代码的目的是测试“响应时间是否符合预期”，如果用真实 API，每次响应时间可能不一样（网络时快时慢），测试就失去了意义；而模拟可以固定延时为 1000ms，精准验证逻辑。


#### 2. 实际项目中如何使用真实接口？
如果需要测试“与真实接口的交互是否正常”（比如集成测试场景），可以直接调用真实接口，但需要注意：  
- **隔离测试环境**：用专门的测试服务器（而非生产环境），避免污染真实数据。  
- **处理异步和异常**：确保接口返回稳定（比如测试前先初始化数据），并捕获网络错误（避免测试崩溃）。  

示例（真实调用写法，去掉模拟即可）：  
```javascript
// 实际项目中，直接使用真实的 fetch 调用，不做模拟
async function askQuestionReal() {
  const res = await fetch('https://test-api.example.com/api/voice-chat/ask', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sessionId: 'real-session', question: '真实问题' }),
  });
  if (!res.ok) throw new Error('接口调用失败');
  return res.json();
}

// 测试用例中不模拟 fetch，直接调用真实函数
test('真实接口调用应返回有效数据', async () => {
  const data = await askQuestionReal();
  expect(data).toHaveProperty('answer'); // 验证返回结构
});
```


### 二、Promise 是什么？
`Promise` 是 JavaScript 中处理**异步操作**的一种机制，用于解决“回调地狱”（多层嵌套的异步逻辑），让异步代码更清晰。  

#### 核心概念：
- 异步操作：不会立即完成的操作（比如 API 调用、文件读取、定时器等），需要等待一段时间才有结果。  
- `Promise` 是一个“容器”，里面保存着某个未来才会结束的异步操作的结果。  

#### 三种状态：
1. **pending（进行中）**：初始状态，操作还没完成。  
2. **fulfilled（已成功）**：操作完成，返回结果（用 `resolve` 触发）。  
3. **rejected（已失败）**：操作出错，返回错误（用 `reject` 触发）。  

#### 代码示例（对应测试中的 `fetch` 模拟）：
```javascript
// 模拟一个异步操作：1秒后返回成功结果
const delayPromise = new Promise((resolve) => {
  setTimeout(() => {
    resolve('操作完成'); // 1秒后将状态改为 fulfilled，返回结果
  }, 1000);
});

// 用 .then() 处理成功结果
delayPromise.then((result) => {
  console.log(result); // 1秒后输出：操作完成
});
```

测试代码中，`fetch` 模拟返回的就是一个 `Promise`，通过 `setTimeout` 模拟异步延时，最后用 `resolve` 返回成功响应。


### 三、为什么调用被测试的函数（`askQuestionMock`）会触发模拟的 `fetch`？
这是 Jest 的**函数模拟（Mock）机制**在起作用，核心是“替换原始函数”：  

1. 在 `beforeEach` 中，代码执行了 `global.fetch = jest.fn();`  
   - 这行代码把全局的 `fetch` 函数（浏览器/Node 环境自带的网络请求函数）替换成了 Jest 生成的“模拟函数”（`jest.fn()` 创建的函数）。  
   - 从此以后，所有代码中调用的 `fetch` 都是这个模拟函数，而不是真实的网络请求函数。  

2. 接着，`fetch.mockImplementationOnce(...)` 定义了“这个模拟函数被调用时的具体行为”  
   - 告诉 Jest：“当 `fetch` 被调用时，返回一个延时 1000ms 的 Promise，且最终返回 `{ answer: 'ok' }`”。  

3. 当调用 `askQuestionMock()` 时，函数内部执行了 `fetch(...)`  
   - 此时的 `fetch` 已经被替换成模拟函数，所以会触发我们定义的模拟行为（延时、返回预设结果），而不会发送真实的网络请求。  


### 总结
- 模拟 API 是为了让测试更稳定，只关注自身代码逻辑；真实调用用于验证与外部接口的集成。  
- `Promise` 是处理异步操作的工具，让异步代码更易读。  
- 调用被测试函数触发模拟 `fetch`，是因为 Jest 替换了原始 `fetch` 函数，接管了它的行为。  

---

# 三、心得体会：第一次写测试代码的收获

作为第一次写测试代码的学生，我从零开始学习测试，遇到了不少问题，也积累了一些经验。以下是我的一些心得体会，希望能帮助到同样刚开始学习测试的同学。

## 1. 刚开始的困惑

### 1.1 为什么要写测试？代码能跑不就行了吗？
最开始我对测试的概念很模糊，觉得"代码能运行、功能正常不就行了吗？为什么还要写测试？"

后来我理解了：
- **测试是为了保证代码质量**：功能能跑不代表在各种情况下都能正常工作，特别是边界情况和异常输入
- **测试是文档**：好的测试用例能清楚说明"这段代码在什么情况下应该做什么"
- **测试能提高开发效率**：修改代码后运行测试，能快速发现是否破坏了原有功能，不用手动一个个功能去验证

在这次项目中，我写的测试主要验证了**接口响应时间**在不同延时、并发场景下是否正常，这在实际使用中很难手动一一验证。

### 1.2 Mock 是什么？为什么要模拟？
刚开始看到 `jest.fn()` 和 `mockImplementation` 时完全懵了，不明白为什么要"假装"调用接口。

后来明白了：
- **真实接口不可控**：真实接口的响应时间不固定（网络波动），返回数据可能变化，这样测试结果就不稳定
- **测试要隔离**：测试应该只关注"被测试代码的逻辑"，不应该依赖外部服务是否正常
- **可以精准控制场景**：比如我想测试"接口延时 1ms"和"接口延时 3000ms"的场景，用 Mock 可以精确模拟

## 2. 遇到的问题和解决方法

### 2.1 测试一直失败：`expect(elapsed).toBeGreaterThanOrEqual(delay)` 报错
**问题描述**：当 `delay=1ms` 时，测试失败，提示 `Expected: >= 1, Received: 0.068ms`

**原因分析**：
- JavaScript 的计时器精度有限（通常 4-5ms）
- `setTimeout` 即使设置为 1ms，实际可能立即执行或只延迟几微秒
- `performance.now()` 的精度也可能不足

**解决方法**：
```javascript
// 原本写法：严格等于 delay
expect(elapsed).toBeGreaterThanOrEqual(delay);

// 修正后：允许小的误差范围
const expectedMin = Math.max(0, delay - 5);  // 允许 5ms 的下限误差
expect(elapsed).toBeGreaterThanOrEqual(expectedMin);
```

**心得**：测试不是数学题，要考虑实际环境的限制，允许合理的误差范围。

### 2.2 并发测试的总耗时判断错误
**问题描述**：我以为并发 10 个请求，每个延时 1500ms，总耗时应该是 1500ms × 10 = 15000ms

**原因分析**：误解了并发的概念。并发请求是**同时发起**的，所以：
- 10 个请求同时发出，它们都等 1500ms 后返回
- 总耗时 = 最慢的那个请求的时间 ≈ 1500ms（加上调度开销）

**解决方法**：
```javascript
// 错误理解：总耗时 = delay × concurrency
// 正确理解：并发执行，总耗时 ≈ delay（加上少量调度开销）
const slack = 400; // 允许并发调度/计时抖动
expect(elapsed).toBeLessThan(delayForTimer + slack);
```

**心得**：写测试前要理解清楚业务逻辑，特别是异步和并发的概念。

### 2.3 测试输出看不到详细信息
**问题描述**：运行测试后只看到 "PASS" 或 "FAIL"，看不到具体的耗时、期望值等信息

**解决方法**：
- 在测试用例中添加 `console.log` 打印关键信息
- 使用 `console.table` 在测试结束后统一打印结果表格
- 运行测试时加参数：`--verbose --silent=false`

**心得**：测试不仅要能运行，还要能看到详细的执行信息，便于调试和填写测试报告。

### 2.4 边界值测试的用例数量计算
**问题描述**：单缺陷假设下的测试用例数量应该是 2×4+1=9 个，但最开始我理解错了，生成了很多冗余用例

**正确理解**：
- **单缺陷假设**：每次只让一个变量取极值，其他变量保持正常值（中值）
- 对于双变量：每个变量取 4 个极值（最小值、次小值、次大值、最大值），共 4+4+1=9 个用例
- 如果让两个变量都取极值，会产生笛卡尔积（比如 5×5=25 个用例），但这不是边界值测试的标准做法

**心得**：测试用例的设计要遵循理论方法（如边界值分析、等价类划分），不能随意组合。

## 3. 学到的经验

### 3.1 测试要分层设计
这次我设计了几个层次的测试：
1. **单变量边界值测试**：只改变延时，验证基础功能
2. **单变量健壮性测试**：加入无效输入（如负数、超出范围的值）
3. **双变量边界值测试**：考虑两个变量的组合影响
4. **双变量健壮性测试**：在边界值基础上加入异常值

这样逐步扩展，能更全面地覆盖各种场景。

### 3.2 断言要合理，不要太严格
刚开始我写的断言很严格，比如 `expect(elapsed).toBe(1000)`，结果经常失败。

后来学会了：
- 给测试留出合理的误差范围（比如 ±250ms）
- 考虑环境因素（不同机器的性能、CI 环境的网络波动）
- 重点关注"功能是否正常"，而不是精确到毫秒

### 3.3 测试代码也要可读
测试代码不仅是为了验证功能，也是文档。所以要：
- 给测试用例起清晰的名称，说明测试的场景
- 添加注释解释为什么这样设计测试
- 打印输出要格式清晰，便于理解

### 3.4 学会看错误信息
Jest 的错误信息很详细，要学会从中提取关键信息：
- 看到 `Expected: >= 1, Received: 0.068` 就能定位是计时精度问题
- 看到 `fetch.mockImplementationOnce is not a function` 说明 Mock 设置有问题

## 4. 对未来学习的建议

### 4.1 从简单开始，逐步复杂化
- 先写一个最简单的测试（比如 1+1=2），确保测试环境配置正确
- 再写单个场景的测试（比如延时 1000ms）
- 最后写复杂的参数化测试和边界值测试

### 4.2 理解测试理论
- 学习边界值分析、等价类划分等测试设计方法
- 理解单缺陷假设、多缺陷假设的区别
- 知道什么时候用单元测试、集成测试、端到端测试

### 4.3 多动手实践
- 理论学再多，不写代码也掌握不好
- 遇到问题先自己思考，再查文档，最后问老师
- 记录遇到的问题和解决方法，形成自己的知识库

### 4.4 保持耐心
测试代码调试起来可能比业务代码还麻烦，特别是异步测试和 Mock 相关的。保持耐心，一步步排查问题，总能解决的。

## 5. 总结

第一次写测试代码虽然遇到不少困难，但收获很大：

1. **理解了测试的价值**：测试不仅是验证功能，更是保证代码质量的手段
2. **学会了异步测试**：掌握了 Promise、async/await 在测试中的应用
3. **理解了 Mock 机制**：知道如何模拟外部依赖，让测试更稳定
4. **掌握了测试设计方法**：学会了边界值分析、单缺陷假设等理论
5. **提高了代码质量意识**：写测试的过程中，也发现了业务代码的一些潜在问题

虽然这次测试可能还有改进空间，但作为第一次尝试，我觉得已经很不错了。继续学习，继续改进！

---

