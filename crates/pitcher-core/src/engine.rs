use std::path::PathBuf;

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Engine {
    Speed,
    Finer,
    Finest,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum PitchQuality {
    Speed,
    Quality,
    Consistency,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Backend {
    Ffmpeg,
    RubberBandLinked,
}

impl Backend {
    pub fn detect() -> Backend {
        Backend::Ffmpeg
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ShiftRequest {
    pub input: PathBuf,
    pub output: PathBuf,
    pub cents: i32,
    pub formant: bool,
    pub engine: Engine,
    pub pitch_quality: PitchQuality,
    pub section: Option<(f64, f64)>,
    pub output_format: Option<String>,
}

pub fn shift(_req: &ShiftRequest) -> anyhow::Result<()> {
    unimplemented!()
}
