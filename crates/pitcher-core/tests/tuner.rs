use std::path::PathBuf;
use std::process::Command;

use pitcher_core::tuner::{detect_at, detect_range, detect_samples, Method};

fn tmp_dir(tag: &str) -> PathBuf {
    let dir = std::env::temp_dir().join(format!("pitcher-tuner-{tag}-{}", std::process::id()));
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

fn make_tone(path: &PathBuf, hz: f64) {
    let status = Command::new("ffmpeg")
        .args(["-hide_banner", "-loglevel", "error", "-y"])
        .args(["-f", "lavfi", "-i"])
        .arg(format!("sine=frequency={hz}:duration=2:sample_rate=44100"))
        .arg(path)
        .status()
        .unwrap();
    assert!(status.success(), "ffmpeg failed to synthesize tone");
}

fn sine(hz: f64, sr: u32, secs: f64) -> Vec<f64> {
    let n = (sr as f64 * secs) as usize;
    (0..n)
        .map(|i| {
            let t = i as f64 / sr as f64;
            0.6 * (2.0 * std::f64::consts::PI * hz * t).sin()
        })
        .collect()
}

#[test]
fn yin_detects_a4_from_samples() {
    let samples = sine(440.0, 44100, 0.5);
    let reading = detect_samples(&samples, 44100, Method::Yin).unwrap();
    assert!(
        (reading.hz - 440.0).abs() < 2.0,
        "expected 440, got {}",
        reading.hz
    );
    assert_eq!(reading.note.name, "A4");
    assert!(reading.note.cents_off.abs() < 5.0);
    assert!(reading.confidence > 0.5);
}

#[test]
fn yin_detects_csharp4_from_samples() {
    let hz = 277.1826309768721;
    let samples = sine(hz, 44100, 0.5);
    let reading = detect_samples(&samples, 44100, Method::Yin).unwrap();
    assert!(
        (reading.hz - hz).abs() < 2.0,
        "expected {}, got {}",
        hz,
        reading.hz
    );
    assert_eq!(reading.note.name, "C#4");
    assert!(reading.note.cents_off.abs() < 5.0);
}

#[test]
fn yin_detects_low_note() {
    let samples = sine(110.0, 44100, 0.5);
    let reading = detect_samples(&samples, 44100, Method::Yin).unwrap();
    assert_eq!(reading.note.name, "A2");
    assert!((reading.hz - 110.0).abs() < 1.5);
}

#[test]
fn yin_detects_c5_from_samples() {
    let samples = sine(523.2511306011972, 44100, 0.5);
    let reading = detect_samples(&samples, 44100, Method::Yin).unwrap();
    assert_eq!(reading.note.name, "C5");
}

#[test]
fn silence_returns_no_pitch() {
    let samples = vec![0.0f64; 22050];
    let reading = detect_samples(&samples, 44100, Method::Yin).unwrap();
    assert!(reading.hz <= 0.0 || reading.confidence < 0.2);
}

#[test]
fn detect_at_reads_a_file() {
    if !have("ffmpeg") {
        return;
    }
    let dir = tmp_dir("at");
    let input = dir.join("tone.wav");
    make_tone(&input, 440.0);

    let reading = detect_at(&input, 1.0, 250, Method::Yin).unwrap().unwrap();
    assert!((reading.hz - 440.0).abs() < 3.0, "got {}", reading.hz);
    assert_eq!(reading.note.name, "A4");
}

#[test]
fn detect_range_returns_frames() {
    if !have("ffmpeg") {
        return;
    }
    let dir = tmp_dir("range");
    let input = dir.join("tone.wav");
    make_tone(&input, 440.0);

    let frames = detect_range(&input, 0.0, 1.5, Method::Yin).unwrap();
    assert!(!frames.is_empty(), "expected frames");
    let voiced: Vec<_> = frames.iter().filter(|f| f.reading.hz > 0.0).collect();
    assert!(!voiced.is_empty(), "expected voiced frames");
    let avg: f64 = voiced.iter().map(|f| f.reading.hz).sum::<f64>() / voiced.len() as f64;
    assert!((avg - 440.0).abs() < 5.0, "avg {avg}");
}

#[test]
fn detect_at_rejects_out_of_range() {
    if !have("ffmpeg") {
        return;
    }
    let dir = tmp_dir("oob");
    let input = dir.join("tone.wav");
    make_tone(&input, 440.0);
    assert!(detect_at(&input, 100.0, 250, Method::Yin).is_err());
}
