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

pub fn sanitize_filename(s: &str) -> String {
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
        "untitled".to_string()
    } else {
        trimmed.to_string()
    }
}

pub fn download_filename(
    track_title: &str,
    artist: Option<&str>,
    source_path: &str,
    variant_name: Option<&str>,
    cents: i32,
    ext: &str,
) -> String {
    let title = track_title.trim();
    let song = if title.is_empty() {
        std::path::Path::new(source_path)
            .file_stem()
            .and_then(|s| s.to_str())
            .unwrap_or("audio")
            .to_string()
    } else {
        title.to_string()
    };

    let mut parts = vec![sanitize_filename(&song)];
    if let Some(a) = artist {
        let a = a.trim();
        if !a.is_empty() {
            parts.push(sanitize_filename(a));
        }
    }
    let pitch = match variant_name.map(str::trim) {
        Some(n) if !n.is_empty() => sanitize_filename(n),
        _ => format!("{cents:+}"),
    };
    parts.push(pitch);

    format!("{}.{}", parts.join(" - "), ext)
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Bookmark {
    pub id: i64,
    pub track_id: i64,
    pub t: f64,
    pub name: Option<String>,
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
