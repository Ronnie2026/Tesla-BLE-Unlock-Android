#!/usr/bin/env python3
"""Parse a btsnoop_hci.log and report LE advertising reports, flagging Tesla key ads."""
import re, struct, sys

def records(data):
    assert data[:8] == b'btsnoop\x00', 'not a btsnoop file'
    # version(4) datalink(4)
    off = 16
    datalink = struct.unpack('>I', data[12:16])[0]
    while off + 24 <= len(data):
        orig, incl, flags, drops, ts = struct.unpack('>IIIIQ', data[off:off+24])
        off += 24
        pkt = data[off:off+incl]
        off += incl + (orig - incl)
        if orig == 0:
            break
        yield flags, pkt

def adv_reports(pkt):
    # HCI Event packet
    if not pkt or pkt[0] != 0x04:
        return
    code = pkt[1]
    body = pkt[3:]
    if code == 0x3E and body:  # LE meta event
        sub = body[0]
        if sub == 0x01:  # LE Advertising Report
            n = body[1]; off = 2
            for _ in range(n):
                if off + 1 > len(body): break
                etype, = struct.unpack('B', body[off:off+1]); off += 1
                atype = body[off]; off += 1
                addr = body[off:off+6].hex(':'); off += 6
                dlen = body[off]; off += 1
                data = body[off:off+dlen]; off += dlen
                rssi = struct.unpack('b', body[off:off+1])[0]; off += 1
                yield ('legacy', etype, atype, addr, data, rssi)
        elif sub == 0x0D:  # LE Extended Advertising Report
            n = body[1]; off = 2
            for _ in range(n):
                if off + 1 > len(body): break
                off += 1  # event type config (2 bytes) -> skip 1, read 2
                if off + 8 > len(body): break
                etype, = struct.unpack('H', body[off:off+2]); off += 2
                atype = body[off]; off += 1
                addr = body[off:off+6].hex(':'); off += 6
                ptype = body[off]; off += 1
                off += 1  # primary phy
                sphy = body[off]; off += 1
                off += 1  # adv sid
                off += 1  # tx power
                rssi = struct.unpack('b', body[off:off+1])[0]; off += 1
                off += 2  # periodic adv interval
                off += 1  # direct address type
                off += 6  # direct address
                if off + 1 > len(body): break
                dlen = body[off]; off += 1
                if off + dlen > len(body): break
                data = body[off:off+dlen]; off += dlen
                yield ('ext', etype, atype, addr, data, rssi)

def parse_ad_structure_names(data):
    out, off = [], 0
    while off < len(data):
        ln = data[off]
        if ln == 0: break
        chunk = data[off+1:off+1+ln]
        if chunk and chunk[0] == 0x09:
            out.append(chunk[1:].decode('utf-8', 'replace'))
        off += 1 + ln
    return out

def main(path, pattern=None):
    data = open(path, 'rb').read()
    if data[:2] == b'PK':
        sys.exit('zip given; extract btsnoop_hci.log first')
    pat = pattern.encode() if pattern else None
    total = 0
    names = {}
    tesla = []
    for flags, pkt in records(data):
        for kind, etype, atype, addr, adata, rssi in adv_reports(pkt):
            total += 1
            ad_names = parse_ad_structure_names(adata)
            for nm in ad_names:
                names.setdefault(nm, [0, rssi])
                names[nm][0] += 1
                names[nm][1] = max(names[nm][1], rssi)
            if (pat and pat in adata) or any(re.match(r'^S[0-9a-fA-F]{16}.$', nm) for nm in ad_names):
                tesla.append((kind, addr, adata.hex(), rssi))
    print(f'LE advertising reports: {total}')
    print(f'\n-- all advertised names (count, best rssi) --')
    for nm, (c, r) in sorted(names.items(), key=lambda kv: -kv[1][1]):
        print(f'  {r:4d} dBm x{c:4d}  {nm!r}')
    print(f'\n-- Tesla-flagged (name/service hits): {len(tesla)}')
    for t in tesla[:20]:
        print('  ', t)

if __name__ == '__main__':
    if len(sys.argv) < 2:
        sys.exit('usage: parse_btsnoop.py <btsnoop_hci.log> [ascii-name-pattern]')
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)
