use std::path::{Path, PathBuf};
use std::process::Command;

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum ExportFormat {
    Wav,
    Flac,
    Mp3,
    Opus,
    M4a,
}

impl ExportFormat {
    pub fn extension(self) -> &'static str {
        match self {
            ExportFormat::Wav => "wav",
            ExportFormat::Flac => "flac",
            ExportFormat::Mp3 => "mp3",
            ExportFormat::Opus => "opus",
            ExportFormat::M4a => "m4a",
        }
    }

    pub fn parse(s: &str) -> Option<Self> {
        match s.to_ascii_lowercase().as_str() {
            "wav" => Some(ExportFormat::Wav),
            "flac" => Some(ExportFormat::Flac),
            "mp3" => Some(ExportFormat::Mp3),
            "opus" | "ogg" => Some(ExportFormat::Opus),
            "m4a" | "aac" => Some(ExportFormat::M4a),
            _ => None,
        }
    }

    pub fn is_lossless(self) -> bool {
        matches!(self, ExportFormat::Wav | ExportFormat::Flac)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Quality {
    High,
    Medium,
}

impl Quality {
    pub fn mp3_quality(self) -> &'static str {
        match self {
            Quality::High => "2",
            Quality::Medium => "4",
        }
    }

    pub fn opus_bitrate(self) -> &'static str {
        match self {
            Quality::High => "192k",
            Quality::Medium => "128k",
        }
    }

    pub fn aac_bitrate(self) -> &'static str {
        match self {
            Quality::High => "256k",
            Quality::Medium => "160k",
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ArchiveTarget {
    pub format: ExportFormat,
    pub extension: &'static str,
}

pub fn archive_target(requested: Option<&str>) -> ArchiveTarget {
    let format = requested
        .and_then(ExportFormat::parse)
        .unwrap_or_else(default_archive_format);
    ArchiveTarget {
        format,
        extension: format.extension(),
    }
}

pub fn default_archive_format() -> ExportFormat {
    std::env::var("PITCHER_ARCHIVE_FORMAT")
        .ok()
        .and_then(|s| ExportFormat::parse(&s))
        .unwrap_or(ExportFormat::Opus)
}

pub fn default_export_format() -> ExportFormat {
    std::env::var("PITCHER_EXPORT_FORMAT")
        .ok()
        .and_then(|s| ExportFormat::parse(&s))
        .unwrap_or(ExportFormat::Opus)
}

pub fn compress_to(
    input: &Path,
    out_dir: &Path,
    format: ExportFormat,
    quality: Quality,
) -> anyhow::Result<(PathBuf, u64)> {
    std::fs::create_dir_all(out_dir)?;
    let stem = input
        .file_stem()
        .and_then(|s| s.to_str())
        .unwrap_or("audio");
    let out = out_dir.join(format!("{stem}.{}", format.extension()));

    let mut cmd = Command::new("ffmpeg");
    cmd.args(["-hide_banner", "-loglevel", "error", "-y", "-i"]);
    cmd.arg(input);
    apply_codec(&mut cmd, format, quality);
    cmd.arg(&out);

    let output = cmd.output()?;
    if !output.status.success() {
        anyhow::bail!(
            "ffmpeg compress failed: {}",
            String::from_utf8_lossy(&output.stderr).trim()
        );
    }
    let bytes = std::fs::metadata(&out)?.len();
    Ok((out, bytes))
}

pub fn apply_codec(cmd: &mut Command, format: ExportFormat, quality: Quality) {
    match format {
        ExportFormat::Wav => {
            cmd.args(["-c:a", "pcm_s16le"]);
        }
        ExportFormat::Flac => {
            cmd.args(["-c:a", "flac", "-compression_level", "8"]);
        }
        ExportFormat::Mp3 => {
            cmd.args(["-c:a", "libmp3lame", "-q:a", quality.mp3_quality()]);
        }
        ExportFormat::Opus => {
            cmd.args(["-c:a", "libopus", "-b:a", quality.opus_bitrate()]);
        }
        ExportFormat::M4a => {
            cmd.args(["-c:a", "aac", "-b:a", quality.aac_bitrate()]);
        }
    }
}
