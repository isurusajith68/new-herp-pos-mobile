#!/usr/bin/env python3
"""
Fake Bluetooth thermal printer for testing the POS app without real hardware.

The phone connects to your PC over Bluetooth (SPP). Windows routes that
connection to an "incoming" Bluetooth COM port; this script reads that COM port
and prints a decoded view of every ESC/POS job the app sends.

Usage:
    pip install pyserial
    python fake_printer.py COM5          # use your incoming BT COM port
    python fake_printer.py COM5 --raw    # also show raw bytes (hex)

Find your COM port: Windows → Bluetooth settings → "More Bluetooth options"
→ COM Ports tab → the "Incoming" entry (create one if missing).
"""

import sys
import time

try:
    import serial  # pyserial
except ImportError:
    sys.exit("pyserial not installed. Run:  pip install pyserial")

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


def main():
    if len(sys.argv) < 2:
        sys.exit("Usage: python fake_printer.py <COM_PORT> [--raw]")
    port = sys.argv[1]
    show_raw = "--raw" in sys.argv

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
                print("=" * 40)
                print(f"  PRINT JOB  ({len(buf)} bytes)  {time.strftime('%H:%M:%S')}")
                print("=" * 40)
                if show_raw:
                    print(buf.hex(" "))
                    print("-" * 40)
                decoded_text = decode(bytes(buf))
                print(decoded_text)
                print("=" * 40 + "\n")

                # Save to receipts directory
                import os
                os.makedirs("receipts", exist_ok=True)
                filename = f"receipts/receipt_{time.strftime('%Y%m%d_%H%M%S')}.txt"
                with open(filename, "w", encoding="utf-8") as f:
                    f.write(decoded_text)
                print(f"💾 Saved receipt to: {filename}\n")

                buf.clear()
                last = None
    except KeyboardInterrupt:
        print("\nStopped.")
    finally:
        ser.close()


if __name__ == "__main__":
    main()
