use pitcher_core::archive::{
    archive_target, compress_to, default_archive_format, default_export_format, ExportFormat,
    Quality,
};

#[test]
fn archive_target_defaults_to_opus() {
    let t = archive_target(None);
    assert_eq!(t.extension, "opus");
    assert_eq!(t.format, ExportFormat::Opus);
}

#[test]
fn default_formats_are_opus() {
    std::env::remove_var("PITCHER_ARCHIVE_FORMAT");
    std::env::remove_var("PITCHER_EXPORT_FORMAT");
    assert_eq!(default_archive_format(), ExportFormat::Opus);
    assert_eq!(default_export_format(), ExportFormat::Opus);
}

#[test]
fn archive_target_respects_lossless_request() {
    let t = archive_target(Some("flac"));
    assert_eq!(t.extension, "flac");
    let w = archive_target(Some("wav"));
    assert_eq!(w.extension, "wav");
}

#[test]
fn export_formats_have_extensions() {
    assert_eq!(ExportFormat::Flac.extension(), "flac");
    assert_eq!(ExportFormat::Mp3.extension(), "mp3");
    assert_eq!(ExportFormat::Opus.extension(), "opus");
    assert_eq!(ExportFormat::M4a.extension(), "m4a");
    assert_eq!(ExportFormat::Wav.extension(), "wav");
}

#[test]
fn mp3_high_quality_maps_to_q2() {
    let q = Quality::High;
    assert_eq!(q.mp3_quality(), "2");
}

#[test]
fn opus_high_is_192k() {
    let q = Quality::High;
    assert_eq!(q.opus_bitrate(), "192k");
}

#[test]
fn compress_to_flac_creates_smaller_file() {
    if !have("ffmpeg") {
        return;
    }
    let dir = tmp_dir("flac");
    let input = dir.join("tone.wav");
    make_tone(&input, 440.0, 3.0);
    let (out, bytes) = compress_to(&input, &dir, ExportFormat::Flac, Quality::High).unwrap();
    assert!(out.exists());
    assert_eq!(out.extension().unwrap(), "flac");
    let wav_size = std::fs::metadata(&input).unwrap().len();
    assert!(
        bytes < wav_size,
        "flac {bytes} should be smaller than wav {wav_size}"
    );
}

#[test]
fn opus_is_much_smaller_than_flac_for_broadband_audio() {
    if !have("ffmpeg") {
        return;
    }
    let dir = tmp_dir("opussize");
    let input = dir.join("noise.wav");
    make_noise(&input, 5.0);
    let (_, flac_bytes) = compress_to(&input, &dir, ExportFormat::Flac, Quality::High).unwrap();
    let (_, opus_bytes) = compress_to(&input, &dir, ExportFormat::Opus, Quality::High).unwrap();
    assert!(
        opus_bytes < flac_bytes,
        "opus {opus_bytes} should be smaller than flac {flac_bytes}"
    );
}

#[test]
fn compress_to_mp3_creates_smaller_file() {
    if !have("ffmpeg") {
        return;
    }
    let dir = tmp_dir("mp3");
    let input = dir.join("tone.wav");
    make_tone(&input, 440.0, 3.0);
    let (out, bytes) = compress_to(&input, &dir, ExportFormat::Mp3, Quality::High).unwrap();
    assert!(out.exists());
    let wav_size = std::fs::metadata(&input).unwrap().len();
    assert!(bytes < wav_size);
}

fn have(cmd: &str) -> bool {
    std::process::Command::new("sh")
        .arg("-c")
        .arg(format!("command -v {cmd}"))
        .status()
        .map(|s| s.success())
        .unwrap_or(false)
}

fn tmp_dir(tag: &str) -> std::path::PathBuf {
    let dir = std::env::temp_dir().join(format!("pitcher-archive-{tag}-{}", std::process::id()));
    std::fs::create_dir_all(&dir).unwrap();
    dir
}

fn make_tone(path: &std::path::Path, hz: f64, secs: f64) {
    let status = std::process::Command::new("ffmpeg")
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

fn make_noise(path: &std::path::Path, secs: f64) {
    let status = std::process::Command::new("ffmpeg")
        .args(["-hide_banner", "-loglevel", "error", "-y"])
        .args(["-f", "lavfi", "-i"])
        .arg(format!(
            "anoisesrc=duration={secs}:sample_rate=44100:amplitude=0.5"
        ))
        .arg(path)
        .status()
        .unwrap();
    assert!(status.success());
}
