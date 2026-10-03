use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Track {
    pub id: i64,
    pub source_path: String,
    pub source_kind: String,
    pub source_url: Option<String>,
    pub title: String,
    pub artist: Option<String>,
    pub duration_s: f64,
    pub sample_rate: Option<i64>,
    pub created_at: String,
    pub variant_count: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Variant {
    pub id: i64,
    pub track_id: i64,
    pub name: Option<String>,
    pub cents: i32,
    pub formant: bool,
    pub engine: String,
    pub pitch_quality: String,
    pub section_start: Option<f64>,
    pub section_end: Option<f64>,
    pub output_path: String,
    pub output_format: Option<String>,
    pub src_note: Option<String>,
    pub src_hz: Option<f64>,
    pub target_note: Option<String>,
    pub target_hz: Option<f64>,
    pub favorite: bool,
    pub created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VariantSpec {
    pub cents: i32,
    pub formant: bool,
    pub engine: String,
    pub pitch_quality: String,
    pub section: Option<(f64, f64)>,
    pub output_path: String,
    pub output_format: Option<String>,
    pub src_note: Option<String>,
    pub src_hz: Option<f64>,
    pub target_note: Option<String>,
    pub target_hz: Option<f64>,
}
