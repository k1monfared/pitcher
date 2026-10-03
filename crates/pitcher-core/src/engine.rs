use std::path::{Path, PathBuf};
use std::process::Command;

use serde::{Deserialize, Serialize};

use crate::notes::ratio_from_cents;

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

impl ShiftRequest {
    pub fn new(input: impl Into<PathBuf>, output: impl Into<PathBuf>, cents: i32) -> Self {
        ShiftRequest {
            input: input.into(),
            output: output.into(),
            cents,
            formant: false,
            engine: Engine::Finer,
            pitch_quality: PitchQuality::Quality,
            section: None,
            output_format: None,
        }
    }

    pub fn ratio(&self) -> f64 {
        ratio_from_cents(self.cents)
    }
}

fn pitchq_flag(q: PitchQuality) -> &'static str {
    match q {
        PitchQuality::Speed => "speed",
        PitchQuality::Quality => "quality",
        PitchQuality::Consistency => "consistency",
    }
}

pub fn rubberband_filter(req: &ShiftRequest) -> String {
    let formant = if req.formant { "preserved" } else { "shifted" };
    format!(
        "rubberband=pitch={}:formant={}:pitchq={}:transients=crisp",
        req.ratio(),
        formant,
        pitchq_flag(req.pitch_quality),
    )
}

fn run(mut cmd: Command) -> anyhow::Result<()> {
    let out = cmd.output()?;
    if !out.status.success() {
        anyhow::bail!(
            "ffmpeg failed: {}",
            String::from_utf8_lossy(&out.stderr).trim()
        );
    }
    Ok(())
}

pub fn shift(req: &ShiftRequest) -> anyhow::Result<()> {
    if let Some((start, end)) = req.section {
        if end <= start {
            anyhow::bail!("section end ({end}) must be greater than start ({start})");
        }
    }

    let filter = rubberband_filter(req);

    let mut cmd = Command::new("ffmpeg");
    cmd.args(["-hide_banner", "-loglevel", "error", "-y"]);

    if let Some((start, end)) = req.section {
        cmd.args(["-ss", &format!("{start}")]);
        cmd.args(["-t", &format!("{}", end - start)]);
    }

    cmd.arg("-i").arg(&req.input);
    cmd.args(["-af", &filter]);

    apply_format(req, &mut cmd);
    cmd.arg(&req.output);

    run(cmd)
}

fn apply_format(req: &ShiftRequest, cmd: &mut Command) {
    match req.output_format.as_deref() {
        Some("mp3") => {
            cmd.args(["-c:a", "libmp3lame", "-q:a", "2"]);
        }
        Some("flac") => {
            cmd.args(["-c:a", "flac"]);
        }
        Some("ogg") | Some("opus") => {
            cmd.args(["-c:a", "libopus", "-b:a", "192k"]);
        }
        Some("m4a") | Some("aac") => {
            cmd.args(["-c:a", "aac", "-b:a", "256k"]);
        }
        Some("wav") | None => {
            if output_ext_requires_track_quality(&req.output) {
                cmd.args(["-c:a", "pcm_s16le"]);
            }
        }
        Some(_) => {}
    }
}

fn output_ext_requires_track_quality(path: &Path) -> bool {
    path.extension()
        .and_then(|e| e.to_str())
        .map(|e| e.eq_ignore_ascii_case("wav"))
        .unwrap_or(false)
}

pub fn probe_duration(path: &Path) -> anyhow::Result<f64> {
    let out = Command::new("ffprobe")
        .args([
            "-v",
            "error",
            "-show_entries",
            "format=duration",
            "-of",
            "default=nw=1:nk=1",
        ])
        .arg(path)
        .output()?;
    if !out.status.success() {
        anyhow::bail!("ffprobe failed for {}", path.display());
    }
    let s = String::from_utf8_lossy(&out.stdout);
    Ok(s.trim().parse()?)
}
