use std::path::Path;
use std::process::Command;

use serde::{Deserialize, Serialize};

use crate::notes::{hz_to_midi, hz_to_note, NoteReading};

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

const YIN_THRESHOLD: f64 = 0.15;
const SILENCE_RMS: f64 = 1e-3;
const MIN_HZ: f64 = 40.0;
const MAX_HZ: f64 = 2100.0;

pub fn detect_samples(samples: &[f64], sr: u32, _method: Method) -> anyhow::Result<PitchReading> {
    let sr = sr as f64;
    let n = samples.len();
    if n < 64 {
        return Ok(no_pitch());
    }

    let rms = (samples.iter().map(|x| x * x).sum::<f64>() / n as f64).sqrt();
    if rms < SILENCE_RMS {
        return Ok(no_pitch());
    }

    let max_tau = ((sr / MIN_HZ).floor() as usize).min(n / 2);
    let min_tau = (sr / MAX_HZ).ceil() as usize;
    if max_tau <= min_tau + 2 {
        return Ok(no_pitch());
    }

    let window = n.min(2048);
    let buf = &samples[..window];

    let mut d = vec![0.0f64; max_tau + 1];
    for tau in 1..=max_tau {
        let mut sum = 0.0;
        let limit = window - tau;
        for j in 0..limit {
            let diff = buf[j] - buf[j + tau];
            sum += diff * diff;
        }
        d[tau] = sum;
    }

    let mut cmnd = vec![1.0f64; max_tau + 1];
    let mut running = 0.0;
    for tau in 1..=max_tau {
        running += d[tau];
        cmnd[tau] = if running > 0.0 {
            d[tau] * tau as f64 / running
        } else {
            1.0
        };
    }

    let mut tau_est = 0usize;
    let mut tau = min_tau;
    while tau < max_tau {
        if cmnd[tau] < YIN_THRESHOLD {
            while tau + 1 < max_tau && cmnd[tau + 1] < cmnd[tau] {
                tau += 1;
            }
            tau_est = tau;
            break;
        }
        tau += 1;
    }
    if tau_est == 0 {
        return Ok(no_pitch());
    }

    let better = parabolic(&cmnd, tau_est);
    let hz = sr / better;
    if !(MIN_HZ..=MAX_HZ).contains(&hz) {
        return Ok(no_pitch());
    }
    let confidence = (1.0 - cmnd[tau_est]).clamp(0.0, 1.0);

    let midi = hz_to_midi(hz);
    Ok(PitchReading {
        hz,
        midi,
        note: hz_to_note(hz),
        confidence,
    })
}

fn parabolic(cmnd: &[f64], tau: usize) -> f64 {
    if tau == 0 || tau + 1 >= cmnd.len() {
        return tau as f64;
    }
    let x0 = cmnd[tau - 1];
    let x1 = cmnd[tau];
    let x2 = cmnd[tau + 1];
    let denom = 2.0 * (2.0 * x1 - x2 - x0);
    if denom.abs() < 1e-12 {
        return tau as f64;
    }
    let delta = (x2 - x0) / denom;
    tau as f64 + delta.clamp(-1.0, 1.0)
}

fn no_pitch() -> PitchReading {
    PitchReading {
        hz: 0.0,
        midi: 0.0,
        note: NoteReading {
            name: String::new(),
            midi: 0,
            cents_off: 0.0,
        },
        confidence: 0.0,
    }
}

pub fn decode_mono(path: &Path, sr: u32) -> anyhow::Result<Vec<f64>> {
    let out = Command::new("ffmpeg")
        .args(["-hide_banner", "-loglevel", "error", "-i"])
        .arg(path)
        .args(["-f", "f32le", "-ac", "1", "-ar", &sr.to_string(), "-"])
        .output()?;
    if !out.status.success() {
        anyhow::bail!(
            "ffmpeg decode failed: {}",
            String::from_utf8_lossy(&out.stderr).trim()
        );
    }
    Ok(out
        .stdout
        .chunks_exact(4)
        .map(|b| f32::from_le_bytes([b[0], b[1], b[2], b[3]]) as f64)
        .collect())
}

pub fn duration(path: &Path) -> anyhow::Result<f64> {
    crate::engine::probe_duration(path)
}

pub fn detect_at(
    path: &Path,
    at: f64,
    window_ms: u32,
    method: Method,
) -> anyhow::Result<Option<PitchReading>> {
    if at < 0.0 {
        anyhow::bail!("time must be non-negative, got {at}");
    }
    let total = duration(path)?;
    if at > total {
        anyhow::bail!("time {at}s is beyond track duration {total}s");
    }

    let sr = 44100u32;
    let samples = decode_mono(path, sr)?;
    let win = ((window_ms as f64 / 1000.0) * sr as f64) as usize;
    let center = (at * sr as f64) as usize;
    let half = win / 2;
    let start = center.saturating_sub(half);
    let end = (center + half).min(samples.len());
    if end <= start {
        return Ok(None);
    }

    let reading = detect_samples(&samples[start..end], sr, method)?;
    if reading.hz <= 0.0 {
        Ok(None)
    } else {
        Ok(Some(reading))
    }
}

pub fn detect_range(
    path: &Path,
    from: f64,
    to: f64,
    method: Method,
) -> anyhow::Result<Vec<Frame>> {
    if from < 0.0 || to <= from {
        anyhow::bail!("invalid range {from}..{to}");
    }
    let sr = 44100u32;
    let samples = decode_mono(path, sr)?;
    let total = samples.len() as f64 / sr as f64;
    let from = from.min(total);
    let to = to.min(total).max(from);

    let hop = (0.02 * sr as f64) as usize;
    let win = (0.05 * sr as f64) as usize;
    let mut frames = Vec::new();

    let mut center = (from * sr as f64) as usize;
    let end_sample = (to * sr as f64) as usize;
    while center < end_sample && center < samples.len() {
        let start = center.saturating_sub(win / 2);
        let stop = (center + win / 2).min(samples.len());
        if stop > start {
            let reading = detect_samples(&samples[start..stop], sr, method)?;
            frames.push(Frame {
                t: center as f64 / sr as f64,
                reading,
            });
        }
        center += hop;
    }
    Ok(frames)
}
