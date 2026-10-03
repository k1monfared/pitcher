use std::net::TcpListener;

use pitcher_server::{find_free_port, PREFERRED_PORT};

#[test]
fn finds_preferred_when_free() {
    let port = find_free_port(PREFERRED_PORT, 50).unwrap();
    assert!(port >= PREFERRED_PORT);
    assert!(port < PREFERRED_PORT + 50);
}

#[test]
fn skips_busy_port() {
    let listener = TcpListener::bind(("0.0.0.0", 0)).unwrap();
    let busy = listener.local_addr().unwrap().port();
    let port = find_free_port(busy, 50).unwrap();
    assert_ne!(port, busy, "expected to skip the busy port");
    assert!(port > busy);
}

#[test]
fn errors_when_range_exhausted() {
    let listener = TcpListener::bind(("0.0.0.0", 0)).unwrap();
    let busy = listener.local_addr().unwrap().port();
    let err = find_free_port(busy, 1).unwrap_err();
    assert_eq!(err.kind(), std::io::ErrorKind::AddrInUse);
}
