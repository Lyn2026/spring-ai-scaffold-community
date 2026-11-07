"""本地假模型服务：不花额度就能跑通脚手架全链路。

同时模拟两种协议：
  * OpenAI 兼容（/chat/completions、/embeddings）
  * DashScope 原生（/services/aigc/text-generation/generation、/services/embeddings/text-embedding/text-embedding）

启动：python scripts/mock-model-server.py 9999
把 base-url 指向 http://127.0.0.1:9999（OpenAI 兼容）或 http://127.0.0.1:9999/api/v1（原生协议）。
收到的请求体会打印到控制台，方便确认 model 字段到底是哪个。
"""

import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer


def reply_openai(model, content="MOCK_OK"):
    return {
        "id": "mock-chat",
        "object": "chat.completion",
        "model": model,
        "choices": [{"index": 0, "finish_reason": "stop",
                     "message": {"role": "assistant", "content": content}}],
        "usage": {"prompt_tokens": 7, "completion_tokens": 3, "total_tokens": 10},
    }


def reply_native(model, content="MOCK_OK"):
    return {
        "request_id": "mock-native",
        "output": {"finish_reason": "stop",
                   "choices": [{"finish_reason": "stop",
                                "message": {"role": "assistant", "content": content}}]},
        "usage": {"input_tokens": 7, "output_tokens": 3, "total_tokens": 10},
    }


def reply_embedding(dimension=1024):
    return {
        "data": [{"index": 0, "embedding": [0.01] * dimension}],
        "usage": {"prompt_tokens": 5, "total_tokens": 5},
    }


def reply_native_embedding(dimension=1024):
    return {"output": {"embeddings": [{"text_index": 0, "embedding": [0.01] * dimension}]},
            "usage": {"total_tokens": 5}}


class Handler(BaseHTTPRequestHandler):

    def do_POST(self):
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length).decode("utf-8", "replace")
        try:
            body = json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            body = {}

        model = body.get("model") or "(none)"
        path = self.path
        if path.endswith("/chat/completions") or path.endswith("/lock/completions"):
            payload = reply_openai(model)
        elif "/services/aigc/text-generation/generation" in path:
            payload = reply_native(model)
        elif "/services/embeddings/text-embedding/text-embedding" in path:
            payload = reply_native_embedding()
        elif path.endswith("/embeddings"):
            payload = reply_embedding()
        else:
            sys.stdout.write("[MOCK] 未知路径 %s\n" % path)
            self.send_response(404)
            self.end_headers()
            return

        sys.stdout.write("[MOCK] %s model=%s\n" % (path, model))
        sys.stdout.flush()

        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, fmt, *args):
        pass


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 9999
    sys.stdout.write("[MOCK] listening on 127.0.0.1:%d\n" % port)
    sys.stdout.flush()
    HTTPServer(("127.0.0.1", port), Handler).serve_forever()
