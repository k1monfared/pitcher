use serde::{Deserialize, Serialize};

const NOTE_NAMES: [&str; 12] = [
    "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B",
];

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct NoteReading {
    pub name: String,
    pub midi: i32,
    pub cents_off: f64,
}

#[derive(Debug, thiserror::Error, PartialEq)]
pub enum NoteError {
    #[error("invalid note name: {0}")]
    InvalidName(String),
}

pub fn ratio_from_cents(cents: i32) -> f64 {
    2f64.powf(cents as f64 / 1200.0)
}

pub fn hz_to_midi(hz: f64) -> f64 {
    69.0 + 12.0 * (hz / 440.0).log2()
}

pub fn midi_to_hz(midi: f64) -> f64 {
    440.0 * 2f64.powf((midi - 69.0) / 12.0)
}

pub fn midi_to_note(midi: i32) -> String {
    let idx = midi.rem_euclid(12) as usize;
    let octave = midi.div_euclid(12) - 1;
    format!("{}{}", NOTE_NAMES[idx], octave)
}

pub fn hz_to_note(hz: f64) -> NoteReading {
    let m = hz_to_midi(hz);
    let rounded = m.round();
    let midi = rounded as i32;
    let cents_off = 100.0 * (m - rounded);
    NoteReading {
        name: midi_to_note(midi),
        midi,
        cents_off,
    }
}

pub fn note_to_hz(note: &str) -> Result<f64, NoteError> {
    let s = note.trim();
    if s.is_empty() {
        return Err(NoteError::InvalidName(note.to_string()));
    }

    let (pitch_part, offset_cents) = match s.find(['+', '-']) {
        Some(pos) => {
            let (p, off) = s.split_at(pos);
            let off: i32 = off
                .parse()
                .map_err(|_| NoteError::InvalidName(note.to_string()))?;
            (p, off)
        }
        None => (s, 0),
    };

    let bytes = pitch_part.as_bytes();
    if bytes.is_empty() || !bytes[0].is_ascii_uppercase() {
        return Err(NoteError::InvalidName(note.to_string()));
    }
    let letter = (bytes[0] as char).to_string();

    let mut rest = &pitch_part[1..];
    let accidental = if rest.starts_with('#') {
        rest = &rest[1..];
        1
    } else if rest.starts_with('b') {
        rest = &rest[1..];
        -1
    } else {
        0
    };

    if rest.is_empty() {
        return Err(NoteError::InvalidName(note.to_string()));
    }
    let octave: i32 = rest
        .parse()
        .map_err(|_| NoteError::InvalidName(note.to_string()))?;

    let semitone = match letter.as_str() {
        "C" => 0,
        "D" => 2,
        "E" => 4,
        "F" => 5,
        "G" => 7,
        "A" => 9,
        "B" => 11,
        _ => return Err(NoteError::InvalidName(note.to_string())),
    };

    let midi = 12 * (octave + 1) + semitone + accidental;
    let base = midi_to_hz(midi as f64);
    Ok(base * ratio_from_cents(offset_cents))
}

pub fn cents_between_hz(source: f64, target: f64) -> f64 {
    1200.0 * (target / source).log2()
}

pub fn cents_between_notes(source: &str, target: &str) -> Result<f64, NoteError> {
    let s = note_to_hz(source)?;
    let t = note_to_hz(target)?;
    Ok(cents_between_hz(s, t))
}

pub fn semitones_to_cents(semitones: f64) -> f64 {
    semitones * 100.0
}
