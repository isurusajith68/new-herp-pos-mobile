#!/usr/bin/env python3
"""
Fake thermal printer for testing the POS app without real hardware. Supports the
app's two transports and prints a decoded view of every ESC/POS job it receives.

WiFi (raw TCP on port 9100 — matches the app's "Wi-Fi" printer mode):
    python fake_printer.py --tcp            # listen on 0.0.0.0:9100
    python fake_printer.py --tcp 9100 --raw # custom port + hex dump
    In the app: Printer settings → Wi-Fi → enter this PC's LAN IP, port 9100.

Bluetooth (SPP over a Windows "incoming" COM port):
    pip install pyserial
    python fake_printer.py COM5             # use your incoming BT COM port
    python fake_printer.py COM5 --raw       # also show raw bytes (hex)
    Find your COM port: Windows → Bluetooth settings → "More Bluetooth options"
    → COM Ports tab → the "Incoming" entry (create one if missing).
"""

import socket
import sys
import time

ALIGN = {0: "LEFT", 1: "CENTER", 2: "RIGHT"}


def decode(buf: bytes) -> str:
    """Reconstruct the printed text from an ESC/POS byte stream."""
    out = []
    line = []
    bold = False
    align = "LEFT"
    i = 0
    n = len(buf)

    def flush():
        out.append("".join(line))
        line.clear()

    while i < n:
        b = buf[i]
        if b == 0x1B and i + 1 < n:          # ESC
            cmd = buf[i + 1]
            if cmd == 0x40:                   # ESC @  init
                i += 2; continue
            if cmd == 0x61 and i + 2 < n:     # ESC a n  align
                align = ALIGN.get(buf[i + 2], "LEFT"); i += 3; continue
            if cmd == 0x45 and i + 2 < n:     # ESC E n  bold
                next_bold = buf[i + 2] != 0
                if next_bold != bold:
                    line.append("**")
                    bold = next_bold
                i += 3; continue
            i += 2; continue
        if b == 0x1D and i + 1 < n:           # GS
            cmd = buf[i + 1]
            if cmd == 0x21 and i + 2 < n:     # GS ! n  size
                i += 3; continue
            if cmd == 0x56:                   # GS V m [n]  cut
                out.append("")
                out.append("        ----- [CUT] -----")
                i += 3 if i + 2 < n else 2
                continue
            i += 2; continue
        if b == 0x0A:                         # LF
            if bold:
                line.append("**")
            text = "".join(line)
            prefix = ""
            if align == "CENTER":
                prefix = "                "[: max(0, (32 - len(text)) // 2)]
            elif align == "RIGHT":
                prefix = " " * max(0, 32 - len(text))
            out.append(prefix + text)
            line.clear()
            if bold:
                line.append("**")
            i += 1
            continue
        if 0x20 <= b < 0x7F or b >= 0xA0:     # printable
            ch = bytes([b]).decode("latin-1", "replace")
            line.append(ch)
        i += 1

    if line:
        flush()
    return "\n".join(out)


def forward_to_real_printer(printer_name: str, buf: bytes):
    """Forward raw ESC/POS bytes directly to a physical Windows printer."""
    try:
        import win32print
    except ImportError:
        print("❌ Error: 'win32print' library not found. Run 'pip install pywin32' to enable real printer forwarding.")
        return

    try:
        hPrinter = win32print.OpenPrinter(printer_name)
        try:
            hJob = win32print.StartDocPrinter(hPrinter, 1, ("POS Relay Job", None, "RAW"))
            try:
                win32print.StartPagePrinter(hPrinter)
                win32print.WritePrinter(hPrinter, buf)
                win32print.EndPagePrinter(hPrinter)
                print(f"📠 Forwarded raw ESC/POS bytes to real printer: '{printer_name}'")
            finally:
                win32print.EndDocPrinter(hPrinter)
        finally:
            win32print.ClosePrinter(hPrinter)
    except Exception as e:
        print(f"❌ Failed to forward to real printer: {e}")


def handle_job(buf: bytes, show_raw: bool, real_printer: str = None):
    """Decode one ESC/POS job, print it to the terminal, save a copy, and optionally forward it."""
    print("=" * 40)
    print(f"  PRINT JOB  ({len(buf)} bytes)  {time.strftime('%H:%M:%S')}")
    print("=" * 40)
    if show_raw:
        print(buf.hex(" "))
        print("-" * 40)
    decoded_text = decode(buf)
    print(decoded_text)
    print("=" * 40 + "\n")

    if real_printer:
        forward_to_real_printer(real_printer, buf)

    import os
    receipts_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "receipts")
    os.makedirs(receipts_dir, exist_ok=True)
    filename = os.path.join(receipts_dir, f"receipt_{time.strftime('%Y%m%d_%H%M%S')}.txt")
    with open(filename, "w", encoding="utf-8") as f:
        f.write(decoded_text)
    print(f"💾 Saved receipt to: {filename}\n")


def run_tcp(port: int, show_raw: bool, real_printer: str = None):
    """Raw-TCP printer (JetDirect/RAW). Matches the app's Wi-Fi mode.

    Each print is a fresh connection: the app opens the socket, writes the whole
    ESC/POS payload, then closes — so we read one connection to EOF = one job.
    """
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("0.0.0.0", port))
    srv.listen(1)
    print(f"🖨  Fake WiFi printer listening on 0.0.0.0:{port}")
    for ip in local_ips():
        print(f"    → point the app at  {ip}:{port}")
    print("Print from the app to test. Ctrl+C to stop.\n")
    try:
        while True:
            conn, addr = srv.accept()
            buf = bytearray()
            with conn:
                while True:
                    chunk = conn.recv(4096)
                    if not chunk:
                        break
                    buf.extend(chunk)
            if buf:
                print(f"(connection from {addr[0]})")
                handle_job(bytes(buf), show_raw, real_printer)
    except KeyboardInterrupt:
        print("\nStopped.")
    finally:
        srv.close()


def run_serial(port: str, show_raw: bool, real_printer: str = None):
    """Bluetooth-SPP printer, read off a Windows incoming COM port."""
    try:
        import serial  # pyserial
    except ImportError:
        sys.exit("pyserial not installed. Run:  pip install pyserial")

    ser = serial.Serial(port, 115200, timeout=0.2)
    print(f"🖨  Fake printer listening on {port}. Print from the app to test. Ctrl+C to stop.\n")

    buf = bytearray()
    last = None
    try:
        while True:
            data = ser.read(4096)
            if data:
                buf.extend(data)
                last = time.time()
            elif buf and last and time.time() - last > 0.6:
                handle_job(bytes(buf), show_raw, real_printer)
                buf.clear()
                last = None
    except KeyboardInterrupt:
        print("\nStopped.")
    finally:
        ser.close()


def local_ips():
    """Best-effort list of this machine's LAN IPv4 addresses."""
    ips = []
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))  # no packet sent; just picks the outbound iface
        ips.append(s.getsockname()[0])
        s.close()
    except OSError:
        pass
    return ips or ["<this-pc-LAN-IP>"]


def main():
    # Windows consoles default to cp1252, which can't encode the emoji below.
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except (AttributeError, OSError):
        pass

    real_printer = None
    if "--real-printer" in sys.argv:
        idx = sys.argv.index("--real-printer")
        if idx + 1 < len(sys.argv):
            real_printer = sys.argv[idx + 1]
            sys.argv.pop(idx + 1)
            sys.argv.pop(idx)

    args = [a for a in sys.argv[1:] if a != "--raw"]
    show_raw = "--raw" in sys.argv

    if args and args[0] == "--tcp":
        port = int(args[1]) if len(args) > 1 else 9100
        run_tcp(port, show_raw, real_printer)
    elif args:
        run_serial(args[0], show_raw, real_printer)
    else:
        sys.exit(
            "Usage:\n"
            "  python fake_printer.py --tcp [PORT] [--real-printer \"PrinterName\"]   # WiFi printer (default port 9100)\n"
            "  python fake_printer.py <COM_PORT> [--real-printer \"PrinterName\"]     # Bluetooth SPP printer\n"
            "  add --raw to either for a hex dump"
        )


if __name__ == "__main__":
    main()
