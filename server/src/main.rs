use std::net::{IpAddr, Ipv4Addr, SocketAddr};
use std::path::PathBuf;

use pitcher_server::{build_router, find_free_port, AppState, PREFERRED_PORT};

const MAX_SCAN: u16 = 200;

fn lan_ip() -> Option<String> {
    let out = std::process::Command::new("hostname")
        .arg("-I")
        .output()
        .ok()?;
    String::from_utf8_lossy(&out.stdout)
        .split_whitespace()
        .next()
        .map(|s| s.to_string())
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    let data_dir = std::env::var("PITCHER_DATA")
        .map(PathBuf::from)
        .unwrap_or_else(|_| PathBuf::from("data"));
    std::fs::create_dir_all(&data_dir)?;

    let web_dir = std::env::var("PITCHER_WEB")
        .map(PathBuf::from)
        .ok()
        .or_else(|| {
            let d = PathBuf::from("web/dist");
            d.is_dir().then_some(d)
        });

    let state = AppState {
        db: data_dir.join("shelf.sqlite").to_string_lossy().to_string(),
        out_dir: data_dir.join("out"),
        data_dir: data_dir.clone(),
        web_dir,
    };

    let app = build_router(state);

    let port = find_free_port(PREFERRED_PORT, MAX_SCAN)?;
    let addr = SocketAddr::new(IpAddr::V4(Ipv4Addr::UNSPECIFIED), port);
    let listener = tokio::net::TcpListener::bind(addr).await?;

    if port != PREFERRED_PORT {
        println!("port {PREFERRED_PORT} was busy, using {port}");
    }
    println!("pitcher server listening on http://localhost:{port}");
    if let Some(ip) = lan_ip() {
        println!("LAN: http://{ip}:{port}");
    }

    axum::serve(listener, app).await?;
    Ok(())
}
