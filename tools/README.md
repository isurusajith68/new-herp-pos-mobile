# Testing printing without a real printer

Turn your PC into a **fake thermal printer** so you can test the app's print flow
and see the receipt output on screen. `fake_printer.py` decodes each ESC/POS job
and prints it. It supports both of the app's transports — **Wi-Fi** (raw TCP) and
**Bluetooth** (SPP).

## Wi-Fi (matches the app's "Wi-Fi" printer mode) — easiest

The app opens a raw TCP socket to `IP:9100` and writes the ESC/POS bytes — exactly
what a networked thermal printer does. The script listens on that port and decodes
each job. No Bluetooth pairing, no pyserial.

```
Phone (app, Wi-Fi)  ──TCP :9100──►  PC  ──►  fake_printer.py --tcp
```

1. **Put the phone and PC on the same Wi-Fi network.**
2. **Run the fake printer:**
   ```bash
   python fake_printer.py --tcp          # listens on 0.0.0.0:9100
   ```
   It prints your PC's LAN IP, e.g. `→ point the app at 192.168.1.6:9100`.
   (Add `--raw` for a hex dump; pass a port as `--tcp 9100` to change it.)
3. **In the app:** Printer settings → **Wi-Fi** → enter that IP and port `9100`
   → **Print test receipt**, or place an order.
4. If nothing arrives, allow Python through the **Windows Firewall** (it usually
   prompts on first run — tick *Private networks*), and confirm both devices are
   on the same subnet (guest Wi-Fi often blocks device-to-device traffic).

## Bluetooth (SPP over a Windows incoming COM port)

The phone connects to your PC over Bluetooth (SPP). Windows hands that connection
to an **incoming Bluetooth COM port**, and `fake_printer.py` reads that port and
prints a decoded view of each job.

```
Phone (app, SPP client)  ──Bluetooth──►  PC incoming COM port  ──►  fake_printer.py
```

### Steps (Windows)

1. **Pair the phone and PC.**
   - PC: Settings → Bluetooth → Add device → pair with your phone.

2. **Create an incoming Bluetooth COM port on the PC.**
   - Settings → Bluetooth → **More Bluetooth options** → **COM Ports** tab.
   - **Add… → Incoming (device initiates the connection) → OK**.
   - Note the port it creates, e.g. `COM5`.

3. **Run the fake printer.**
   ```bash
   pip install pyserial
   python fake_printer.py COM5
   ```
   (add `--raw` to also dump the hex bytes)

4. **In the app:**
   - Open **Printer settings** (printer icon).
   - Grant the Bluetooth permission when asked.
   - Your **PC's name** appears in the paired-devices list — select it.
   - Tap **Print test receipt**, or place an order.

The decoded receipt appears in your terminal, e.g.:

```
========================================
  PRINT JOB  (214 bytes)  14:32:10
========================================
              TEST PRINT
--------------------------------
Item                         Qty
Sample burger                  1
Cola                           2
--------------------------------
            HERP POS

        ----- ✂ CUT -----
========================================
```

## Notes

- `*text*` marks bold segments; `CENTER`/`RIGHT` lines are padded to ~32 cols.
- Every job is also saved to `tools/receipts/` as a timestamped `.txt`.
- Wi-Fi is usually the fastest way to iterate on receipt layout — Windows
  Bluetooth SPP can be fiddly.
- Baud rate is irrelevant over Bluetooth SPP — any value works.
- If the PC doesn't show up in the app (Bluetooth), re-pair and make sure the
  **Incoming** COM port exists.
