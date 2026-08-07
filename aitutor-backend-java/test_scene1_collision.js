import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    cache_collision: {
      executor: 'per-vu-iterations',
      vus: 50,
      iterations: 1,
      maxDuration: '5s',
    },
  },
};

export default function () {
  const url = 'http://localhost:8080/api/voice-chat/ask';
  const payload = JSON.stringify({
    courseId: 'testCourse',
    question: '这是一句需要被合并的高频测试提问',
  });
  
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'X-User-Id': 'same_user_collision_test',
    },
  };

  const res = http.post(url, payload, params);

  check(res, {
    'status is 200': (r) => r.status === 200,
    'status is SUCCESS': (r) => r.json('status') === 'SUCCESS',
    'response time < 300ms': (r) => r.timings.duration < 300,
  });
}
