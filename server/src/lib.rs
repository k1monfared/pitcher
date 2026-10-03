use std::net::{IpAddr, Ipv4Addr, SocketAddr, TcpListener};
use std::path::PathBuf;

use axum::extract::{Path, Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::routing::{delete, get, patch, post};
use axum::{Json, Router};
use serde::{Deserialize, Serialize};

use pitcher_core::engine::{Engine, PitchQuality, ShiftRequest};
use pitcher_core::notes::{cents_between_hz, hz_to_note, note_to_hz};
use pitcher_core::shelf::Shelf;
use pitcher_core::tuner::{detect_at, duration, Method};

#[derive(Clone)]
pub struct AppState {
    pub db: String,
    pub out_dir: PathBuf,
    pub data_dir: PathBuf,
    pub web_dir: Option<PathBuf>,
}

impl AppState {
    pub fn shelf(&self) -> anyhow::Result<Shelf> {
        Shelf::open(&self.db)
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ImportRequest {
    pub path: Option<String>,
    pub url: Option<String>,
    pub title: Option<String>,
    pub artist: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ShiftBody {
    #[serde(default)]
    pub cents: i32,
    #[serde(default)]
    pub formant: bool,
    pub format: Option<String>,
    pub to_note: Option<String>,
    pub from_note: Option<String>,
    pub from_hz: Option<f64>,
    pub to_hz: Option<f64>,
    pub section: Option<(f64, f64)>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DetectBody {
    pub path: Option<String>,
    pub track: Option<i64>,
    pub at: f64,
    pub window_ms: Option<u32>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StarBody {
    pub favorite: bool,
}

#[derive(Debug, Deserialize)]
pub struct IntervalQuery {
    pub source: Option<String>,
    pub target: Option<String>,
    pub from_hz: Option<f64>,
    pub to_hz: Option<f64>,
}

#[derive(Debug, Deserialize)]
pub struct NoteQuery {
    pub hz: f64,
}

pub const PREFERRED_PORT: u16 = 7373;

pub fn find_free_port(start: u16, max_scan: u16) -> std::io::Result<u16> {
    for offset in 0..max_scan {
        let port = start + offset;
        let addr = SocketAddr::new(IpAddr::V4(Ipv4Addr::UNSPECIFIED), port);
        if TcpListener::bind(addr).is_ok() {
            return Ok(port);
        }
    }
    Err(std::io::Error::new(
        std::io::ErrorKind::AddrInUse,
        format!("no free port in {start}..{}", start + max_scan),
    ))
}

pub fn build_router(state: AppState) -> Router {
    let api = build_api(state.clone());
    match &state.web_dir {
        Some(dir) if dir.is_dir() => {
            let index = dir.join("index.html");
            api.fallback_service(
                tower_http::services::ServeDir::new(dir)
                    .not_found_service(tower_http::services::ServeFile::new(index)),
            )
        }
        _ => api,
    }
}

pub fn build_api(state: AppState) -> Router {
    Router::new()
        .route("/api/health", get(health))
        .route("/api/tracks", get(list_tracks))
        .route("/api/tracks/{id}", get(get_track))
        .route("/api/tracks/{id}/variants", get(list_variants))
        .route("/api/tracks/{id}/shift", post(shift_track))
        .route("/api/tracks/{id}", delete(delete_track))
        .route("/api/tracks/{id}", patch(rename_track))
        .route("/api/import", post(import))
        .route("/api/detect", post(detect))
        .route("/api/note", get(note))
        .route("/api/interval", get(interval))
        .route("/api/media/{variant_id}", get(media))
        .route("/api/tracks/{id}/audio", get(track_audio))
        .route("/api/variants/{id}/star", post(star))
        .route("/api/variants/{id}", patch(rename_variant))
        .route("/api/variants/{id}", delete(delete_variant))
        .route("/api/tracks/{id}/export", get(export_all))
        .with_state(state)
}

struct ApiError(anyhow::Error);

impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        let msg = self.0.to_string();
        let code = if msg.contains("not found") {
            StatusCode::NOT_FOUND
        } else {
            StatusCode::BAD_REQUEST
        };
        (code, Json(serde_json::json!({ "error": msg }))).into_response()
    }
}

impl From<anyhow::Error> for ApiError {
    fn from(e: anyhow::Error) -> Self {
        ApiError(e)
    }
}

impl From<serde_json::Error> for ApiError {
    fn from(e: serde_json::Error) -> Self {
        ApiError(e.into())
    }
}

impl From<std::io::Error> for ApiError {
    fn from(e: std::io::Error) -> Self {
        ApiError(e.into())
    }
}

impl From<pitcher_core::notes::NoteError> for ApiError {
    fn from(e: pitcher_core::notes::NoteError) -> Self {
        ApiError(e.into())
    }
}

impl From<zip::result::ZipError> for ApiError {
    fn from(e: zip::result::ZipError) -> Self {
        ApiError(anyhow::anyhow!("zip error: {e}"))
    }
}

type ApiResult<T> = Result<T, ApiError>;

async fn health() -> &'static str {
    "ok"
}

async fn list_tracks(State(st): State<AppState>) -> ApiResult<Json<serde_json::Value>> {
    let shelf = st.shelf()?;
    Ok(Json(serde_json::to_value(shelf.list_tracks()?)?))
}

async fn get_track(
    State(st): State<AppState>,
    Path(id): Path<i64>,
) -> ApiResult<Json<serde_json::Value>> {
    let shelf = st.shelf()?;
    let track = shelf
        .get_track(id)?
        .ok_or_else(|| anyhow::anyhow!("track {id} not found"))?;
    let variants = shelf.list_variants(id)?;
    Ok(Json(
        serde_json::json!({ "track": track, "variants": variants }),
    ))
}

async fn list_variants(
    State(st): State<AppState>,
    Path(id): Path<i64>,
) -> ApiResult<Json<serde_json::Value>> {
    let shelf = st.shelf()?;
    Ok(Json(serde_json::to_value(shelf.list_variants(id)?)?))
}

async fn delete_track(
    State(st): State<AppState>,
    Path(id): Path<i64>,
) -> ApiResult<Json<serde_json::Value>> {
    let shelf = st.shelf()?;
    if !shelf.delete_track_with_files(id, &st.data_dir)? {
        return Err(ApiError(anyhow::anyhow!("track {id} not found")));
    }
    Ok(Json(serde_json::json!({ "deleted": id })))
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RenameBody {
    pub title: Option<String>,
    pub artist: Option<String>,
}

async fn rename_track(
    State(st): State<AppState>,
    Path(id): Path<i64>,
    Json(body): Json<RenameBody>,
) -> ApiResult<Json<serde_json::Value>> {
    let shelf = st.shelf()?;
    if shelf.get_track(id)?.is_none() {
        return Err(ApiError(anyhow::anyhow!("track {id} not found")));
    }
    shelf.rename_track(id, body.title.as_deref(), body.artist.as_deref())?;
    let track = shelf.get_track(id)?.unwrap();
    Ok(Json(serde_json::json!({ "track": track })))
}

async fn import(
    State(st): State<AppState>,
    Json(body): Json<ImportRequest>,
) -> ApiResult<Json<serde_json::Value>> {
    let shelf = st.shelf()?;

    let (source_path, source_kind, source_url) = if let Some(path) = &body.path {
        (path.clone(), "file".to_string(), None)
    } else if let Some(url) = &body.url {
        let dir = st.data_dir.join("imports");
        std::fs::create_dir_all(&dir)?;
        let downloaded = pitcher_core::import::download(url, &dir)?;
        (
            downloaded.to_string_lossy().to_string(),
            "url".to_string(),
            Some(url.clone()),
        )
    } else {
        return Err(ApiError(anyhow::anyhow!("provide path or url")));
    };

    let dur = duration(std::path::Path::new(&source_path))?;
    let title = body
        .title
        .clone()
        .or_else(|| {
            std::path::Path::new(&source_path)
                .file_stem()
                .and_then(|s| s.to_str())
                .map(|s| s.to_string())
        })
        .unwrap_or_else(|| "untitled".to_string());
    let artist = body.artist.clone().unwrap_or_default();

    let id = shelf.add_track(
        &source_path,
        &source_kind,
        source_url.as_deref(),
        &title,
        &artist,
        dur,
        44100,
    )?;
    let track = shelf.get_track(id)?.unwrap();
    Ok(Json(serde_json::json!({ "track": track })))
}

async fn detect(
    State(st): State<AppState>,
    Json(body): Json<DetectBody>,
) -> ApiResult<Json<serde_json::Value>> {
    let path = if let Some(p) = &body.path {
        PathBuf::from(p)
    } else if let Some(id) = body.track {
        let shelf = st.shelf()?;
        let track = shelf
            .get_track(id)?
            .ok_or_else(|| anyhow::anyhow!("track {id} not found"))?;
        PathBuf::from(track.source_path)
    } else {
        return Err(ApiError(anyhow::anyhow!("provide path or track")));
    };

    let window = body.window_ms.unwrap_or(250);
    match detect_at(&path, body.at, window, Method::Yin)? {
        Some(reading) => Ok(Json(serde_json::to_value(reading)?)),
        None => Ok(Json(serde_json::json!({ "hz": 0.0, "note": null }))),
    }
}

async fn note(Query(q): Query<NoteQuery>) -> ApiResult<Json<serde_json::Value>> {
    let n = hz_to_note(q.hz);
    Ok(Json(serde_json::json!({ "hz": q.hz, "note": n })))
}

async fn interval(Query(q): Query<IntervalQuery>) -> ApiResult<Json<serde_json::Value>> {
    let cents = if let (Some(f), Some(t)) = (q.from_hz, q.to_hz) {
        cents_between_hz(f, t)
    } else if let (Some(f), Some(t)) = (q.source.as_deref(), q.target.as_deref()) {
        cents_between_hz(note_to_hz(f)?, note_to_hz(t)?)
    } else {
        return Err(ApiError(anyhow::anyhow!(
            "provide source/target or from_hz/to_hz"
        )));
    };
    Ok(Json(serde_json::json!({ "cents": cents })))
}

async fn shift_track(
    State(st): State<AppState>,
    Path(id): Path<i64>,
    Json(body): Json<ShiftBody>,
) -> ApiResult<Json<serde_json::Value>> {
    let shelf = st.shelf()?;
    let track = shelf
        .get_track(id)?
        .ok_or_else(|| anyhow::anyhow!("track {id} not found"))?;

    let cents = resolve_shift(&track.source_path, &body)?;
    let format = body.format.clone().unwrap_or_else(|| {
        pitcher_core::archive::default_export_format()
            .extension()
            .to_string()
    });
    let stem = std::path::Path::new(&track.source_path)
        .file_stem()
        .and_then(|s| s.to_str())
        .unwrap_or("out");
    let section_tag = match body.section {
        Some((s, e)) => format!("_{s:.2}-{e:.2}"),
        None => String::new(),
    };
    let filename = format!("{stem}_{cents:+}{section_tag}.{format}");
    std::fs::create_dir_all(&st.out_dir)?;
    let out_path = st.out_dir.join(&filename);

    let req = ShiftRequest {
        input: track.source_path.clone().into(),
        output: out_path.clone(),
        cents,
        formant: body.formant,
        engine: Engine::Finer,
        pitch_quality: PitchQuality::Quality,
        section: body.section,
        output_format: Some(format.clone()),
    };
    pitcher_core::engine::shift(&req)?;

    let spec = pitcher_core::model::VariantSpec {
        cents,
        formant: body.formant,
        engine: "finer".into(),
        pitch_quality: "quality".into(),
        section: body.section,
        output_path: out_path.to_string_lossy().to_string(),
        output_format: Some(format),
        src_note: None,
        src_hz: None,
        target_note: body
            .to_note
            .clone()
            .or_else(|| body.to_hz.map(|h| hz_to_note(h).name)),
        target_hz: body.to_hz,
    };
    let vid = shelf.add_variant_full(id, &spec)?;
    let variant = shelf.get_variant(vid)?.unwrap();
    Ok(Json(serde_json::to_value(variant)?))
}

fn resolve_shift(source_path: &str, body: &ShiftBody) -> anyhow::Result<i32> {
    if let (Some(f), Some(t)) = (body.from_hz, body.to_hz) {
        return Ok(cents_between_hz(f, t).round() as i32);
    }
    if let (Some(f), Some(t)) = (body.from_note.as_deref(), body.to_note.as_deref()) {
        return Ok(cents_between_hz(note_to_hz(f)?, note_to_hz(t)?).round() as i32);
    }
    if let Some(target) = body.to_note.as_deref() {
        if let Some(f) = body.from_hz {
            return Ok(cents_between_hz(f, note_to_hz(target)?).round() as i32);
        }
        let reading = detect_at(std::path::Path::new(source_path), 0.5, 400, Method::Yin)?
            .ok_or_else(|| anyhow::anyhow!("could not detect source pitch; pass from_note"))?;
        return Ok(cents_between_hz(reading.hz, note_to_hz(target)?).round() as i32);
    }
    Ok(body.cents)
}

#[derive(Debug, Deserialize)]
pub struct FormatQuery {
    pub format: Option<String>,
}

fn target_format(
    requested: Option<&str>,
    stored_ext: &str,
) -> anyhow::Result<pitcher_core::archive::ExportFormat> {
    match requested {
        None => pitcher_core::archive::ExportFormat::parse(stored_ext)
            .ok_or_else(|| anyhow::anyhow!("unknown stored format: {stored_ext}")),
        Some(f) => pitcher_core::archive::ExportFormat::parse(f)
            .ok_or_else(|| anyhow::anyhow!("unknown format: {f}")),
    }
}

fn stored_ext(path: &str) -> String {
    std::path::Path::new(path)
        .extension()
        .and_then(|e| e.to_str())
        .unwrap_or("wav")
        .to_string()
}

fn converted_path(
    out_dir: &std::path::Path,
    variant_id: i64,
    format: pitcher_core::archive::ExportFormat,
) -> PathBuf {
    out_dir
        .join(".converted")
        .join(format!("{variant_id}.{}", format.extension()))
}

fn serve_variant_file(
    out_dir: &std::path::Path,
    variant: &pitcher_core::model::Variant,
    requested: Option<&str>,
) -> anyhow::Result<(Vec<u8>, &'static str, String)> {
    let ext = stored_ext(&variant.output_path);
    let target = target_format(requested, &ext)?;
    if target.extension() == ext {
        let bytes = std::fs::read(&variant.output_path)?;
        return Ok((
            bytes,
            mime_for(Some(target.extension())),
            filename_for(variant, &ext),
        ));
    }
    let cached = converted_path(out_dir, variant.id, target);
    if !cached.is_file() {
        if let Some(parent) = cached.parent() {
            std::fs::create_dir_all(parent)?;
        }
        let (tmp, _) = pitcher_core::archive::compress_to(
            std::path::Path::new(&variant.output_path),
            cached.parent().unwrap(),
            target,
            pitcher_core::archive::Quality::High,
        )?;
        if tmp != cached {
            std::fs::rename(&tmp, &cached)?;
        }
    }
    let bytes = std::fs::read(&cached)?;
    Ok((
        bytes,
        mime_for(Some(target.extension())),
        filename_for(variant, target.extension()),
    ))
}

fn filename_for(variant: &pitcher_core::model::Variant, ext: &str) -> String {
    let stem = variant.name.as_deref().unwrap_or("").trim();
    if stem.is_empty() {
        format!("pitch {:+}.{}", variant.cents, ext)
    } else {
        format!("{}.{ext}", safe_filename(stem))
    }
}

async fn media(
    State(st): State<AppState>,
    Path(variant_id): Path<i64>,
    Query(q): Query<FormatQuery>,
) -> ApiResult<Response> {
    if let Some(f) = q.format.as_deref() {
        pitcher_core::archive::ExportFormat::parse(f)
            .ok_or_else(|| anyhow::anyhow!("unknown format: {f}"))?;
    }
    let shelf = st.shelf()?;
    let variant = shelf
        .get_variant(variant_id)?
        .ok_or_else(|| anyhow::anyhow!("variant {variant_id} not found"))?;
    let (bytes, mime, filename) = serve_variant_file(&st.out_dir, &variant, q.format.as_deref())?;
    Ok((
        [
            (axum::http::header::CONTENT_TYPE, mime),
            (
                axum::http::header::CONTENT_DISPOSITION,
                &format!("attachment; filename=\"{filename}\"")[..],
            ),
        ],
        bytes,
    )
        .into_response())
}

fn mime_for(format: Option<&str>) -> &'static str {
    match format {
        Some("mp3") => "audio/mpeg",
        Some("flac") => "audio/flac",
        Some("ogg") | Some("opus") => "audio/ogg",
        Some("m4a") | Some("aac") => "audio/mp4",
        _ => "audio/wav",
    }
}

fn mime_for_path(path: &str) -> &'static str {
    let ext = std::path::Path::new(path)
        .extension()
        .and_then(|e| e.to_str())
        .map(|e| e.to_ascii_lowercase())
        .unwrap_or_default();
    match ext.as_str() {
        "mp3" => "audio/mpeg",
        "flac" => "audio/flac",
        "ogg" | "opus" => "audio/ogg",
        "m4a" | "aac" => "audio/mp4",
        "wav" => "audio/wav",
        _ => "application/octet-stream",
    }
}

async fn track_audio(State(st): State<AppState>, Path(id): Path<i64>) -> ApiResult<Response> {
    let shelf = st.shelf()?;
    let track = shelf
        .get_track(id)?
        .ok_or_else(|| anyhow::anyhow!("track {id} not found"))?;
    let bytes = std::fs::read(&track.source_path).map_err(|_| {
        anyhow::anyhow!(
            "track {id} audio file not found on disk: {}",
            track.source_path
        )
    })?;
    let mime = mime_for_path(&track.source_path);
    let filename = std::path::Path::new(&track.source_path)
        .file_name()
        .and_then(|s| s.to_str())
        .unwrap_or("audio");
    Ok((
        [
            (axum::http::header::CONTENT_TYPE, mime),
            (
                axum::http::header::CONTENT_DISPOSITION,
                &format!("attachment; filename=\"{filename}\"")[..],
            ),
        ],
        bytes,
    )
        .into_response())
}

async fn star(
    State(st): State<AppState>,
    Path(id): Path<i64>,
    Json(body): Json<StarBody>,
) -> ApiResult<Json<serde_json::Value>> {
    let shelf = st.shelf()?;
    if shelf.get_variant(id)?.is_none() {
        return Err(ApiError(anyhow::anyhow!("variant {id} not found")));
    }
    shelf.set_favorite(id, body.favorite)?;
    Ok(Json(
        serde_json::json!({ "id": id, "favorite": body.favorite }),
    ))
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VariantPatchBody {
    pub name: Option<String>,
    pub favorite: Option<bool>,
}

async fn rename_variant(
    State(st): State<AppState>,
    Path(id): Path<i64>,
    Json(body): Json<VariantPatchBody>,
) -> ApiResult<Json<serde_json::Value>> {
    let shelf = st.shelf()?;
    if shelf.get_variant(id)?.is_none() {
        return Err(ApiError(anyhow::anyhow!("variant {id} not found")));
    }
    if let Some(name) = body.name.as_deref() {
        shelf.rename_variant(id, name)?;
    }
    if let Some(favorite) = body.favorite {
        shelf.set_favorite(id, favorite)?;
    }
    let variant = shelf.get_variant(id)?.unwrap();
    Ok(Json(serde_json::to_value(variant)?))
}

fn safe_filename(s: &str) -> String {
    let cleaned: String = s
        .chars()
        .map(|c| {
            if c == '/' || c == '\\' || c == '\0' {
                '_'
            } else {
                c
            }
        })
        .collect();
    let trimmed = cleaned.trim();
    if trimmed.is_empty() {
        "track".to_string()
    } else {
        trimmed.to_string()
    }
}

async fn export_all(
    State(st): State<AppState>,
    Path(id): Path<i64>,
    Query(q): Query<FormatQuery>,
) -> ApiResult<Response> {
    let shelf = st.shelf()?;
    let track = shelf
        .get_track(id)?
        .ok_or_else(|| anyhow::anyhow!("track {id} not found"))?;
    let variants = shelf.list_variants(id)?;
    if variants.is_empty() {
        return Err(ApiError(anyhow::anyhow!(
            "track {id} has no variants to export"
        )));
    }

    use std::io::Write;
    let mut buf = std::io::Cursor::new(Vec::new());
    {
        let mut zip = zip::ZipWriter::new(&mut buf);
        let options = zip::write::SimpleFileOptions::default();
        for v in &variants {
            let (bytes, _, filename) = serve_variant_file(&st.out_dir, v, q.format.as_deref())
                .map_err(|e| anyhow::anyhow!("variant {}: {e}", v.id))?;
            zip.start_file(filename, options)?;
            zip.write_all(&bytes)?;
        }
        zip.finish()?;
    }
    let bytes = buf.into_inner();
    let filename = format!("{}.zip", safe_filename(&track.title));
    Ok((
        [
            (axum::http::header::CONTENT_TYPE, "application/zip"),
            (
                axum::http::header::CONTENT_DISPOSITION,
                &format!("attachment; filename=\"{filename}\"")[..],
            ),
        ],
        bytes,
    )
        .into_response())
}

async fn delete_variant(
    State(st): State<AppState>,
    Path(id): Path<i64>,
) -> ApiResult<Json<serde_json::Value>> {
    let shelf = st.shelf()?;
    shelf.delete_variant(id)?;
    Ok(Json(serde_json::json!({ "deleted": id })))
}
