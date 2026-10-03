pub struct NoteReading {
    pub name: String,
    pub midi: i32,
    pub cents_off: f64,
}

pub fn ratio_from_cents(_cents: i32) -> f64 {
    unimplemented!()
}

pub fn hz_to_midi(_hz: f64) -> f64 {
    unimplemented!()
}

pub fn midi_to_hz(_midi: f64) -> f64 {
    unimplemented!()
}

pub fn midi_to_note(_midi: i32) -> String {
    unimplemented!()
}

pub fn hz_to_note(_hz: f64) -> NoteReading {
    unimplemented!()
}

pub fn note_to_hz(_note: &str) -> Result<f64, String> {
    unimplemented!()
}

pub fn cents_between_hz(_source: f64, _target: f64) -> f64 {
    unimplemented!()
}

pub fn cents_between_notes(_source: &str, _target: &str) -> Result<f64, String> {
    unimplemented!()
}

pub fn semitones_to_cents(_semitones: f64) -> f64 {
    unimplemented!()
}
