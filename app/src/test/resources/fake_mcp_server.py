# A minimal MCP server over stdio for tests: newline-delimited JSON-RPC on stdin/stdout.
import json, sys

for line in sys.stdin:
    message = json.loads(line)
    method, mid = message.get("method"), message.get("id")
    if mid is None:
        continue
    if method == "initialize":
        result = {"protocolVersion": "2025-06-18", "capabilities": {"tools": {}}, "serverInfo": {"name": "fake", "version": "1"}, "instructions": "Fake server"}
    elif method == "tools/list":
        result = {"tools": [{"name": "shout", "description": "Upper-cases text", "inputSchema": {"type": "object", "properties": {"text": {"type": "string"}}}}]}
    elif method == "tools/call":
        sys.stderr.write("calling shout\n")
        result = {"content": [{"type": "text", "text": message["params"]["arguments"]["text"].upper()}], "isError": False}
    else:
        print(json.dumps({"jsonrpc": "2.0", "id": mid, "error": {"code": -32601, "message": "no such method"}}), flush=True)
        continue
    print(json.dumps({"jsonrpc": "2.0", "id": mid, "result": result}), flush=True)
