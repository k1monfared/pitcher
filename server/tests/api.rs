use std::path::PathBuf;
use std::process::Command;

use axum::body::Body;
use axum::http::{Request, StatusCode};
use http_body_util::BodyExt;
use tower::ServiceExt;

use pitcher_server::{build_router, AppState};

fn tmp_dir(tag: &str) -> PathBuf {
    let dir = std::env::temp_dir().join(format!("pitcher-server-{tag}-{}", std::process::id()));
    std::fs::create_dir_all(&dir).unwrap();
    dir
}

fn have(cmd: &str) -> bool {
    Command::new("sh")
        .arg("-c")
        .arg(format!("command -v {cmd}"))
        .status()
        .map(|s| s.success())
        .unwrap_or(false)
}

fn make_tone(path: &PathBuf, hz: f64, secs: f64) {
    let status = Command::new("ffmpeg")
        .args(["-hide_banner", "-loglevel", "error", "-y"])
        .args(["-f", "lavfi", "-i"])
        .arg(format!(
            "sine=frequency={hz}:duration={secs}:sample_rate=44100"
        ))
        .arg(path)
        .status()
        .unwrap();
    assert!(status.success());
}

async fn body_json(resp: axum::response::Response) -> serde_json::Value {
    let bytes = resp.into_body().collect().await.unwrap().to_bytes();
    serde_json::from_slice(&bytes).unwrap()
}

async fn body_text(resp: axum::response::Response) -> String {
    let bytes = resp.into_body().collect().await.unwrap().to_bytes();
    String::from_utf8_lossy(&bytes).to_string()
}

fn state(tag: &str) -> (AppState, PathBuf) {
    let dir = tmp_dir(tag);
    let db = dir.join("shelf.sqlite");
    let out = dir.join("out");
    std::fs::create_dir_all(&out).unwrap();
    (
        AppState {
            db: db.to_str().unwrap().to_string(),
            out_dir: out.clone(),
            data_dir: dir.clone(),
            web_dir: None,
        },
        dir,
    )
}

#[tokio::test]
async fn serves_web_index_when_present() {
    let dir = tmp_dir("static");
    let web = dir.join("web");
    std::fs::create_dir_all(&web).unwrap();
    std::fs::write(web.join("index.html"), "<h1>pitcher</h1>").unwrap();
    let st = AppState {
        db: dir.join("shelf.sqlite").to_str().unwrap().to_string(),
        out_dir: dir.join("out"),
        data_dir: dir.clone(),
        web_dir: Some(web),
    };
    let app = build_router(st);
    let resp = app
        .oneshot(Request::get("/").body(Body::empty()).unwrap())
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    assert!(body_text(resp).await.contains("pitcher"));
}

#[tokio::test]
async fn unknown_route_404_without_web() {
    let (st, _d) = state("noroute");
    let app = build_router(st);
    let resp = app
        .oneshot(Request::get("/nope").body(Body::empty()).unwrap())
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::NOT_FOUND);
}

#[tokio::test]
async fn health_ok() {
    let (st, _d) = state("health");
    let app = build_router(st);
    let resp = app
        .oneshot(Request::get("/api/health").body(Body::empty()).unwrap())
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
}

#[tokio::test]
async fn tracks_starts_empty() {
    let (st, _d) = state("empty");
    let app = build_router(st);
    let resp = app
        .oneshot(Request::get("/api/tracks").body(Body::empty()).unwrap())
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let v = body_json(resp).await;
    assert_eq!(v.as_array().unwrap().len(), 0);
}

#[tokio::test]
async fn import_and_list_track() {
    if !have("ffmpeg") {
        return;
    }
    let (st, dir) = state("import");
    let input = dir.join("in.wav");
    make_tone(&input, 440.0, 1.0);
    let app = build_router(st);

    let payload = serde_json::json!({ "path": input.to_str().unwrap(), "title": "Tone" });
    let resp = app
        .clone()
        .oneshot(
            Request::post("/api/import")
                .header("content-type", "application/json")
                .body(Body::from(payload.to_string()))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK, "{}", body_text(resp).await);

    let resp = app
        .oneshot(Request::get("/api/tracks").body(Body::empty()).unwrap())
        .await
        .unwrap();
    let v = body_json(resp).await;
    assert_eq!(v.as_array().unwrap().len(), 1);
    assert_eq!(v[0]["title"], "Tone");
}

#[tokio::test]
async fn detect_endpoint_returns_note() {
    if !have("ffmpeg") {
        return;
    }
    let (st, dir) = state("detect");
    let input = dir.join("in.wav");
    make_tone(&input, 440.0, 2.0);
    let app = build_router(st);

    let payload = serde_json::json!({ "path": input.to_str().unwrap(), "at": 1.0 });
    let resp = app
        .oneshot(
            Request::post("/api/detect")
                .header("content-type", "application/json")
                .body(Body::from(payload.to_string()))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let v = body_json(resp).await;
    assert_eq!(v["note"]["name"], "A4");
}

#[tokio::test]
async fn note_endpoint_converts() {
    let (st, _d) = state("note");
    let app = build_router(st);
    let resp = app
        .oneshot(
            Request::get("/api/note?hz=440")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let v = body_json(resp).await;
    assert_eq!(v["note"]["name"], "A4");
}

#[tokio::test]
async fn interval_endpoint() {
    let (st, _d) = state("interval");
    let app = build_router(st);
    let resp = app
        .oneshot(
            Request::get("/api/interval?source=C%234&target=C4")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let v = body_json(resp).await;
    assert!((v["cents"].as_f64().unwrap() + 100.0).abs() < 0.5);
}

#[tokio::test]
async fn shift_creates_variant_and_media_serves_it() {
    if !have("ffmpeg") {
        return;
    }
    let (st, dir) = state("shift");
    let input = dir.join("in.wav");
    make_tone(&input, 440.0, 1.0);
    let app = build_router(st);

    let imp = serde_json::json!({ "path": input.to_str().unwrap(), "title": "Tone" });
    app.clone()
        .oneshot(
            Request::post("/api/import")
                .header("content-type", "application/json")
                .body(Body::from(imp.to_string()))
                .unwrap(),
        )
        .await
        .unwrap();

    let payload = serde_json::json!({ "cents": -100, "formant": true, "format": "wav" });
    let resp = app
        .clone()
        .oneshot(
            Request::post("/api/tracks/1/shift")
                .header("content-type", "application/json")
                .body(Body::from(payload.to_string()))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK, "{}", body_text(resp).await);
    let v = body_json(resp).await;
    assert!(v["id"].as_i64().unwrap() > 0);

    let resp = app
        .oneshot(Request::get("/api/media/1").body(Body::empty()).unwrap())
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
}

#[tokio::test]
async fn star_and_delete_variant() {
    if !have("ffmpeg") {
        return;
    }
    let (st, dir) = state("starv");
    let input = dir.join("in.wav");
    make_tone(&input, 440.0, 1.0);
    let app = build_router(st);

    let imp = serde_json::json!({ "path": input.to_str().unwrap(), "title": "Tone" });
    app.clone()
        .oneshot(
            Request::post("/api/import")
                .header("content-type", "application/json")
                .body(Body::from(imp.to_string()))
                .unwrap(),
        )
        .await
        .unwrap();
    let payload = serde_json::json!({ "cents": -100, "format": "wav" });
    app.clone()
        .oneshot(
            Request::post("/api/tracks/1/shift")
                .header("content-type", "application/json")
                .body(Body::from(payload.to_string()))
                .unwrap(),
        )
        .await
        .unwrap();

    let resp = app
        .clone()
        .oneshot(
            Request::post("/api/variants/1/star")
                .header("content-type", "application/json")
                .body(Body::from(
                    serde_json::json!({ "favorite": true }).to_string(),
                ))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);

    let resp = app
        .oneshot(
            Request::delete("/api/variants/1")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
}

#[tokio::test]
async fn missing_track_returns_404() {
    let (st, _d) = state("404");
    let app = build_router(st);
    let resp = app
        .oneshot(Request::get("/api/tracks/999").body(Body::empty()).unwrap())
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::NOT_FOUND);
}

#[tokio::test]
async fn track_audio_serves_original_file() {
    if !have("ffmpeg") {
        return;
    }
    let (st, dir) = state("audio");
    let input = dir.join("in.wav");
    make_tone(&input, 440.0, 1.0);
    let app = build_router(st);

    let imp = serde_json::json!({ "path": input.to_str().unwrap(), "title": "Tone" });
    app.clone()
        .oneshot(
            Request::post("/api/import")
                .header("content-type", "application/json")
                .body(Body::from(imp.to_string()))
                .unwrap(),
        )
        .await
        .unwrap();

    let resp = app
        .oneshot(
            Request::get("/api/tracks/1/audio")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let ctype = resp
        .headers()
        .get("content-type")
        .unwrap()
        .to_str()
        .unwrap()
        .to_string();
    assert!(ctype.starts_with("audio/"), "content-type: {ctype}");
    let bytes = resp.into_body().collect().await.unwrap().to_bytes();
    assert!(!bytes.is_empty(), "expected audio bytes");
}

#[tokio::test]
async fn track_audio_404_for_missing_track() {
    let (st, _d) = state("audio404");
    let app = build_router(st);
    let resp = app
        .oneshot(
            Request::get("/api/tracks/999/audio")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::NOT_FOUND);
}

#[tokio::test]
async fn track_audio_404_when_file_gone() {
    if !have("ffmpeg") {
        return;
    }
    let (st, dir) = state("audiogone");
    let input = dir.join("in.wav");
    make_tone(&input, 440.0, 1.0);
    let app = build_router(st);

    let imp = serde_json::json!({ "path": input.to_str().unwrap(), "title": "Tone" });
    app.clone()
        .oneshot(
            Request::post("/api/import")
                .header("content-type", "application/json")
                .body(Body::from(imp.to_string()))
                .unwrap(),
        )
        .await
        .unwrap();

    std::fs::remove_file(&input).unwrap();
    let resp = app
        .oneshot(
            Request::get("/api/tracks/1/audio")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::NOT_FOUND);
}
