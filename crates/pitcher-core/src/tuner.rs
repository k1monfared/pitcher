use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};

use crate::notes::NoteReading;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Method {
    Yin,
    YinFft,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PitchReading {
    pub hz: f64,
    pub midi: f64,
    pub note: NoteReading,
    pub confidence: f64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Frame {
    pub t: f64,
    pub reading: PitchReading,
}

pub fn detect_samples(_samples: &[f64], _sr: u32, _method: Method) -> anyhow::Result<PitchReading> {
    unimplemented!()
}

pub fn detect_at(
    _path: &Path,
    _at: f64,
    _window_ms: u32,
    _method: Method,
) -> anyhow::Result<Option<PitchReading>> {
    unimplemented!()
}

pub fn detect_range(
    _path: &Path,
    _from: f64,
    _to: f64,
    _method: Method,
) -> anyhow::Result<Vec<Frame>> {
    unimplemented!()
}

pub fn decode_mono(_path: &Path, _sr: u32) -> anyhow::Result<Vec<f64>> {
    unimplemented!()
}

pub fn _unused(_p: PathBuf) {}
