package main

import (
	"bufio"
	"crypto/sha1"
	"encoding/base64"
	"encoding/binary"
	"errors"
	"io"
	"net"
	"net/http"
	"strings"
	"sync"
	"time"
)

const wsGUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

var errWS = errors.New("websocket")

// wsConn is a hijacked WebSocket. Writes are unmasked (server). TCP_NODELAY is set.
type wsConn struct {
	conn    net.Conn
	br      *bufio.Reader
	bw      *bufio.Writer
	writeMu sync.Mutex
	closed  chan struct{}
	once    sync.Once
}

func headerHasToken(v, token string) bool {
	for _, p := range strings.Split(v, ",") {
		if strings.EqualFold(strings.TrimSpace(p), token) {
			return true
		}
	}
	return false
}

func acceptWS(w http.ResponseWriter, r *http.Request) (*wsConn, error) {
	if !headerHasToken(r.Header.Get("Connection"), "upgrade") || !strings.EqualFold(strings.TrimSpace(r.Header.Get("Upgrade")), "websocket") {
		http.Error(w, "upgrade required", http.StatusBadRequest)
		return nil, errWS
	}
	key := strings.TrimSpace(r.Header.Get("Sec-WebSocket-Key"))
	if key == "" {
		http.Error(w, "missing key", http.StatusBadRequest)
		return nil, errWS
	}
	hj, ok := w.(http.Hijacker)
	if !ok {
		http.Error(w, "hijack unsupported", http.StatusInternalServerError)
		return nil, errWS
	}
	conn, rw, err := hj.Hijack()
	if err != nil {
		return nil, err
	}
	if tcp, ok := conn.(*net.TCPConn); ok {
		_ = tcp.SetNoDelay(true)
		_ = tcp.SetKeepAlive(true)
		_ = tcp.SetKeepAlivePeriod(15 * time.Second)
		_ = tcp.SetReadBuffer(256 * 1024)
		_ = tcp.SetWriteBuffer(256 * 1024)
	}
	sum := sha1.Sum([]byte(key + wsGUID))
	accept := base64.StdEncoding.EncodeToString(sum[:])
	if _, err = rw.WriteString("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: " + accept + "\r\n\r\n"); err != nil {
		_ = conn.Close()
		return nil, err
	}
	if err = rw.Flush(); err != nil {
		_ = conn.Close()
		return nil, err
	}
	return &wsConn{conn: conn, br: rw.Reader, bw: rw.Writer, closed: make(chan struct{})}, nil
}

func (c *wsConn) Close() {
	if c == nil {
		return
	}
	c.once.Do(func() {
		close(c.closed)
		_ = c.conn.Close()
	})
}

func (c *wsConn) writeFrame(opcode byte, payload []byte) error {
	if c == nil {
		return errWS
	}
	c.writeMu.Lock()
	defer c.writeMu.Unlock()
	n := len(payload)
	hdr := make([]byte, 2, 10)
	hdr[0] = 0x80 | (opcode & 0x0f)
	switch {
	case n < 126:
		hdr[1] = byte(n)
	case n <= 65535:
		hdr[1] = 126
		hdr = append(hdr, byte(n>>8), byte(n))
	default:
		hdr[1] = 127
		var b [8]byte
		binary.BigEndian.PutUint64(b[:], uint64(n))
		hdr = append(hdr, b[:]...)
	}
	if _, err := c.bw.Write(hdr); err != nil {
		return err
	}
	if n > 0 {
		if _, err := c.bw.Write(payload); err != nil {
			return err
		}
	}
	return c.bw.Flush()
}

func (c *wsConn) writeBinary(payload []byte) error { return c.writeFrame(0x2, payload) }
func (c *wsConn) writeText(payload []byte) error   { return c.writeFrame(0x1, payload) }

// readData returns the next text or binary message, answering pings.
func (c *wsConn) readData() (byte, []byte, error) {
	var acc []byte
	var opcode byte
	fragment := false
	for {
		fin, op, payload, err := c.readRaw()
		if err != nil {
			return 0, nil, err
		}
		switch op {
		case 0x9:
			_ = c.writeFrame(0xA, payload)
			continue
		case 0xA:
			continue
		case 0x8:
			return 0, nil, io.EOF
		case 0x0:
			if !fragment {
				return 0, nil, errWS
			}
			acc = append(acc, payload...)
			if len(acc) > 2*1024*1024 {
				return 0, nil, errWS
			}
			if fin {
				return opcode, acc, nil
			}
			continue
		}
		if !fin {
			fragment = true
			opcode = op
			acc = append([]byte{}, payload...)
			continue
		}
		return op, payload, nil
	}
}

func (c *wsConn) readRaw() (fin bool, opcode byte, payload []byte, err error) {
	hdr := make([]byte, 2)
	if _, err = io.ReadFull(c.br, hdr); err != nil {
		return
	}
	fin = hdr[0]&0x80 != 0
	opcode = hdr[0] & 0x0f
	masked := hdr[1]&0x80 != 0
	ln := uint64(hdr[1] & 0x7f)
	switch ln {
	case 126:
		b := make([]byte, 2)
		if _, err = io.ReadFull(c.br, b); err != nil {
			return
		}
		ln = uint64(binary.BigEndian.Uint16(b))
	case 127:
		b := make([]byte, 8)
		if _, err = io.ReadFull(c.br, b); err != nil {
			return
		}
		ln = binary.BigEndian.Uint64(b)
	}
	if ln > 2*1024*1024 {
		err = errWS
		return
	}
	var mask [4]byte
	if masked {
		if _, err = io.ReadFull(c.br, mask[:]); err != nil {
			return
		}
	}
	payload = make([]byte, ln)
	if ln > 0 {
		if _, err = io.ReadFull(c.br, payload); err != nil {
			return
		}
	}
	if masked {
		for i := range payload {
			payload[i] ^= mask[i&3]
		}
	}
	return
}
