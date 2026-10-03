use std::path::{Path, PathBuf};
use std::process::Command;

use pitcher_core::engine::{shift, Backend, Engine, PitchQuality, ShiftRequest};

fn tmp_dir(tag: &str) -> PathBuf {
    let dir = std::env::temp_dir().join(format!("pitcher-engine-{tag}-{}", std::process::id()));
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

fn make_tone(path: &Path, hz: f64) {
    let status = Command::new("ffmpeg")
        .args(["-hide_banner", "-loglevel", "error", "-y"])
        .args(["-f", "lavfi", "-i"])
        .arg(format!("sine=frequency={hz}:duration=2:sample_rate=44100"))
        .arg(path)
        .status()
        .unwrap();
    assert!(status.success(), "ffmpeg failed to synthesize tone");
}

fn read_mono_i16(path: &Path) -> (u32, Vec<f64>) {
    let out = Command::new("ffmpeg")
        .args(["-hide_banner", "-loglevel", "error", "-i"])
        .arg(path)
        .args(["-f", "f32le", "-ac", "1", "-ar", "44100", "-"])
        .output()
        .unwrap();
    assert!(out.status.success(), "ffmpeg decode failed");
    let samples: Vec<f64> = out
        .stdout
        .chunks_exact(4)
        .map(|b| f32::from_le_bytes([b[0], b[1], b[2], b[3]]) as f64)
        .collect();
    (44100, samples)
}

fn goertzel_power(samples: &[f64], sr: f64, target: f64) -> f64 {
    let n = samples.len() as f64;
    let k = (0.5 + (n * target / sr)).floor();
    let w = 2.0 * std::f64::consts::PI * k / n;
    let coeff = 2.0 * w.cos();
    let (mut s0, mut s1, mut s2) = (0.0, 0.0, 0.0);
    for &x in samples {
        s0 = x + coeff * s1 - s2;
        s2 = s1;
        s1 = s0;
    }
    let real = s1 - s2 * w.cos();
    let imag = s2 * w.sin();
    real * real + imag * imag
}

fn dominant_freq(samples: &[f64], sr: f64, lo: f64, hi: f64) -> f64 {
    let mut best = (lo, -1.0);
    let mut f = lo;
    while f <= hi {
        let p = goertzel_power(samples, sr, f);
        if p > best.1 {
            best = (f, p);
        }
        f += 0.5;
    }
    best.0
}

fn base_req(input: PathBuf, output: PathBuf, cents: i32) -> ShiftRequest {
    ShiftRequest {
        input,
        output,
        cents,
        formant: false,
        engine: Engine::Finer,
        pitch_quality: PitchQuality::Quality,
        section: None,
        output_format: None,
    }
}

#[test]
fn ffmpeg_backend_available() {
    assert!(have("ffmpeg"), "ffmpeg is required for the default backend");
}

#[test]
fn shift_down_one_semitone_moves_peak() {
    if !have("ffmpeg") {
        return;
    }
    let dir = tmp_dir("down");
    let input = dir.join("in.wav");
    let output = dir.join("out.wav");
    make_tone(&input, 440.0);

    let mut req = base_req(input.clone(), output.clone(), -100);
    req.formant = true;
    shift(&req).unwrap();

    let (sr, samples) = read_mono_i16(&output);
    let peak = dominant_freq(&samples, sr as f64, 380.0, 460.0);
    let expected = 440.0 * 2f64.powf(-100.0 / 1200.0);
    assert!(
        (peak - expected).abs() < 3.0,
        "expected ~{expected} Hz, measured {peak} Hz"
    );
}

#[test]
fn shift_up_seven_semitones_moves_peak() {
    if !have("ffmpeg") {
        return;
    }
    let dir = tmp_dir("up");
    let input = dir.join("in.wav");
    let output = dir.join("out.wav");
    make_tone(&input, 440.0);

    shift(&base_req(input.clone(), output.clone(), 700)).unwrap();

    let (sr, samples) = read_mono_i16(&output);
    let expected = 440.0 * 2f64.powf(700.0 / 1200.0);
    let peak = dominant_freq(&samples, sr as f64, 600.0, 720.0);
    assert!(
        (peak - expected).abs() < 4.0,
        "expected ~{expected} Hz, measured {peak} Hz"
    );
}

#[test]
fn zero_cents_keeps_peak() {
    if !have("ffmpeg") {
        return;
    }
    let dir = tmp_dir("zero");
    let input = dir.join("in.wav");
    let output = dir.join("out.wav");
    make_tone(&input, 440.0);

    shift(&base_req(input.clone(), output.clone(), 0)).unwrap();

    let (sr, samples) = read_mono_i16(&output);
    let peak = dominant_freq(&samples, sr as f64, 400.0, 480.0);
    assert!((peak - 440.0).abs() < 3.0, "expected 440, measured {peak}");
}

#[test]
fn section_render_is_clipped() {
    if !have("ffmpeg") {
        return;
    }
    let dir = tmp_dir("section");
    let input = dir.join("in.wav");
    let output = dir.join("out.wav");
    make_tone(&input, 440.0);

    let mut req = base_req(input.clone(), output.clone(), 200);
    req.section = Some((0.5, 1.0));
    shift(&req).unwrap();

    let dur = Command::new("ffprobe")
        .args([
            "-v",
            "error",
            "-show_entries",
            "format=duration",
            "-of",
            "default=nw=1:nk=1",
        ])
        .arg(&output)
        .output()
        .unwrap();
    let dur: f64 = String::from_utf8_lossy(&dur.stdout).trim().parse().unwrap();
    assert!(dur <= 0.75, "expected clipped section, got {dur}s");
}

#[test]
fn backend_is_ffmpeg_when_no_ffi_link() {
    assert_eq!(Backend::detect(), Backend::Ffmpeg);
}
