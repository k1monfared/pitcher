use std::path::PathBuf;

use axum::Router;
use serde::{Deserialize, Serialize};

#[derive(Clone)]
pub struct AppState {
    pub db: String,
    pub out_dir: PathBuf,
    pub data_dir: PathBuf,
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

pub fn build_router(state: AppState) -> Router {
    let _ = state;
    Router::new()
}
