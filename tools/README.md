# Testing printing without a real printer

Turn your PC into a **fake Bluetooth thermal printer** so you can test the app's
print flow and see the receipt output on screen.

The phone connects to your PC over Bluetooth (SPP). Windows hands that connection
to an **incoming Bluetooth COM port**, and `fake_printer.py` reads that port and
prints a decoded view of each job.

```
Phone (app, SPP client)  ──Bluetooth──►  PC incoming COM port  ──►  fake_printer.py
```

## Steps (Windows)

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
- Baud rate is irrelevant over Bluetooth SPP — any value works.
- If the PC doesn't show up in the app, re-pair and make sure the **Incoming**
  COM port exists.

## Alternative: Wi-Fi test target (no Bluetooth)

Windows Bluetooth SPP can be fiddly. If you'd rather not deal with it, ask and I
can add a **"Network printer (dev)"** option to the app that sends the same
ESC/POS bytes to your PC over Wi-Fi (`IP:port`), where a tiny TCP script prints
them — usually the fastest way to iterate on receipt layout.
