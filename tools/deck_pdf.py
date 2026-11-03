#!/usr/bin/env python3
"""Print the 25-minute deck to lessons/presentation-25min/presentation-25min.pdf.

Each page of the deck becomes one PDF page, 1280 CSS pixels wide and as tall as
its content, in the order the deck's README lists them. Headless Chrome does
the printing over its DevTools protocol, because only that protocol lets a
page's paper size follow its height; pdfunite (poppler) joins the pages.

Needs Google Chrome (set CHROME to use another binary), pdfunite, and network
access: the two Mermaid diagrams load their renderer from a CDN.

Usage (from a checkout of complete):
    python3 tools/deck_pdf.py
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import socket
import struct
import subprocess
import tempfile
import time
import urllib.request
from pathlib import Path

DECK = Path(__file__).resolve().parent.parent / "lessons" / "presentation-25min"
PAGES = ["index", "overview", "flow-diagram", "extensions", "mcp-apps", "tasks", "agent",
         "runtime-facts", "trace-log", "contact"]
CHROME = os.environ.get("CHROME", "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
PORT = 9333
WIDTH = 1280
# The trace log scrolls inside a 600px box on screen; on paper it shows every entry.
PRINT_CSS = ".log{max-height:none!important;overflow:visible!important}"


class DevTools:
    """A minimal WebSocket client for one DevTools target: enough to call methods and await events."""

    def __init__(self, url: str):
        host_port, path = url[len("ws://"):].split("/", 1)
        host, port = host_port.split(":")
        self.sock = socket.create_connection((host, int(port)))
        key = base64.b64encode(os.urandom(16)).decode()
        self.sock.sendall((f"GET /{path} HTTP/1.1\r\nHost: {host_port}\r\nUpgrade: websocket\r\n"
                           f"Connection: Upgrade\r\nSec-WebSocket-Key: {key}\r\n"
                           "Sec-WebSocket-Version: 13\r\n\r\n").encode())
        response = b""
        while b"\r\n\r\n" not in response:
            response += self.sock.recv(1)
        if b" 101 " not in response.split(b"\r\n")[0]:
            raise ConnectionError(response.decode(errors="replace"))
        self.last_id = 0

    def _read(self, n: int) -> bytes:
        data = b""
        while len(data) < n:
            chunk = self.sock.recv(n - len(data))
            if not chunk:
                raise EOFError("DevTools closed the connection")
            data += chunk
        return data

    def _send(self, message: dict) -> None:
        payload = json.dumps(message).encode()
        header = bytearray([0x81])
        if len(payload) < 126:
            header.append(0x80 | len(payload))
        elif len(payload) < 65536:
            header += bytes([0x80 | 126]) + struct.pack(">H", len(payload))
        else:
            header += bytes([0x80 | 127]) + struct.pack(">Q", len(payload))
        # A client must mask every frame it sends.
        mask = os.urandom(4)
        self.sock.sendall(bytes(header) + mask + bytes(b ^ mask[i % 4] for i, b in enumerate(payload)))

    def _receive(self) -> dict:
        message = b""
        while True:
            first, second = self._read(2)
            length = second & 0x7F
            if length == 126:
                length = struct.unpack(">H", self._read(2))[0]
            elif length == 127:
                length = struct.unpack(">Q", self._read(8))[0]
            message += self._read(length)
            if first & 0x80:
                return json.loads(message)

    def call(self, method: str, params: dict | None = None) -> dict:
        self.last_id += 1
        self._send({"id": self.last_id, "method": method, "params": params or {}})
        while True:
            reply = self._receive()
            if reply.get("id") == self.last_id:
                if "error" in reply:
                    raise RuntimeError(f"{method}: {reply['error']}")
                return reply["result"]

    def wait_for(self, event: str, timeout: float = 30) -> None:
        deadline = time.time() + timeout
        while time.time() < deadline:
            if self._receive().get("method") == event:
                return
        raise TimeoutError(event)


def page_target() -> str:
    for _ in range(100):
        try:
            targets = json.load(urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json"))
            return next(t["webSocketDebuggerUrl"] for t in targets if t["type"] == "page")
        except (OSError, StopIteration):
            time.sleep(0.2)
    raise TimeoutError("Chrome did not open a page target")


def print_deck(out: Path, workdir: Path) -> None:
    chrome = subprocess.Popen([CHROME, "--headless=new", f"--remote-debugging-port={PORT}",
                               f"--window-size={WIDTH},900", f"--user-data-dir={workdir / 'profile'}",
                               "--hide-scrollbars", "about:blank"],
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        tab = DevTools(page_target())
        tab.call("Page.enable")
        tab.call("Emulation.setDeviceMetricsOverride",
                 {"width": WIDTH, "height": 900, "deviceScaleFactor": 1, "mobile": False})
        tab.call("Emulation.setEmulatedMedia", {"media": "screen"})
        parts = []
        for name in PAGES:
            tab.call("Page.navigate", {"url": (DECK / f"{name}.html").as_uri()})
            tab.wait_for("Page.loadEventFired")
            # Mermaid renders after the load event.
            time.sleep(3)
            tab.call("Runtime.evaluate", {"expression":
                     f"document.head.insertAdjacentHTML('beforeend', '<style>{PRINT_CSS}</style>')"})
            time.sleep(0.5)
            height = tab.call("Runtime.evaluate", {"expression": "document.documentElement.scrollHeight",
                                                   "returnByValue": True})["result"]["value"]
            pdf = tab.call("Page.printToPDF", {"printBackground": True, "paperWidth": WIDTH / 96,
                                               "paperHeight": height / 96 + 0.02, "marginTop": 0,
                                               "marginBottom": 0, "marginLeft": 0, "marginRight": 0,
                                               "pageRanges": "1"})
            part = workdir / f"{name}.pdf"
            part.write_bytes(base64.b64decode(pdf["data"]))
            parts.append(str(part))
            print(f"{name}.html: {height}px")
        subprocess.run(["pdfunite", *parts, str(out)], check=True)
    finally:
        chrome.terminate()
        chrome.wait()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", type=Path, default=DECK / "presentation-25min.pdf",
                        help="where to write the PDF (default: %(default)s)")
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="deck-pdf-") as workdir:
        print_deck(args.out, Path(workdir))
    print(f"wrote {args.out}")


if __name__ == "__main__":
    main()
