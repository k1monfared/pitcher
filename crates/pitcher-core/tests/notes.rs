use pitcher_core::notes::{
    cents_between_hz, cents_between_notes, hz_to_midi, hz_to_note, midi_to_hz, midi_to_note,
    note_to_hz, ratio_from_cents, semitones_to_cents,
};

const EPS: f64 = 1e-6;

#[test]
fn ratio_from_cents_zero_is_one() {
    assert!((ratio_from_cents(0) - 1.0).abs() < EPS);
}

#[test]
fn ratio_from_cents_octave_is_two() {
    assert!((ratio_from_cents(1200) - 2.0).abs() < EPS);
    assert!((ratio_from_cents(-1200) - 0.5).abs() < EPS);
}

#[test]
fn ratio_from_cents_semitone() {
    let r = ratio_from_cents(100);
    assert!((r - 2f64.powf(1.0 / 12.0)).abs() < EPS);
    assert!((r - 1.0594630943592953).abs() < 1e-12);
}

#[test]
fn hz_midi_roundtrip() {
    assert!((hz_to_midi(440.0) - 69.0).abs() < EPS);
    assert!((midi_to_hz(69.0) - 440.0).abs() < EPS);
    for m in 0..128 {
        let hz = midi_to_hz(m as f64);
        assert!((hz_to_midi(hz) - m as f64).abs() < 1e-9);
    }
}

#[test]
fn a4_is_440() {
    assert!((note_to_hz("A4").unwrap() - 440.0).abs() < EPS);
}

#[test]
fn middle_c_is_c4() {
    assert!((midi_to_hz(60.0) - 261.6255653005986).abs() < 1e-9);
    assert!((note_to_hz("C4").unwrap() - midi_to_hz(60.0)).abs() < EPS);
    let n = hz_to_note(261.6255653005986);
    assert_eq!(n.name, "C4");
    assert!(n.cents_off.abs() < 1e-6);
}

#[test]
fn note_names_include_sharps() {
    assert_eq!(midi_to_note(61), "C#4");
    assert_eq!(midi_to_note(70), "A#4");
    assert_eq!(midi_to_note(72), "C5");
}

#[test]
fn hz_to_note_reports_cents_off() {
    let n = hz_to_note(277.18);
    assert_eq!(n.name, "C#4");
    assert!(n.cents_off.abs() < 2.0);
}

#[test]
fn hz_to_note_flat_side() {
    let n = hz_to_note(440.0 * 2f64.powf(-30.0 / 1200.0));
    assert_eq!(n.name, "A4");
    assert!((n.cents_off + 30.0).abs() < 1e-6);
}

#[test]
fn cents_between_hz_octave() {
    assert!((cents_between_hz(220.0, 440.0) - 1200.0).abs() < 1e-6);
    assert!((cents_between_hz(440.0, 220.0) + 1200.0).abs() < 1e-6);
}

#[test]
fn cents_between_hz_csharp_to_c() {
    let csharp4 = note_to_hz("C#4").unwrap();
    let c4 = note_to_hz("C4").unwrap();
    let c = cents_between_hz(csharp4, c4);
    assert!((c + 100.0).abs() < 1e-6);
}

#[test]
fn cents_between_notes_csharp4_to_g5() {
    let c = cents_between_notes("C#4", "G5").unwrap();
    assert!((c - 1800.0).abs() < 1e-6);
}

#[test]
fn semitones_to_cents_fractional() {
    assert!((semitones_to_cents(1.0 / 8.0) - 12.5).abs() < EPS);
    assert!((semitones_to_cents(1.0 / 16.0) - 6.25).abs() < EPS);
    assert!((semitones_to_cents(5.0 + 1.0 / 16.0) - 506.25).abs() < EPS);
    assert!((semitones_to_cents(-100.0 / 100.0) + 100.0).abs() < EPS);
}

#[test]
fn note_to_hz_rejects_bad_name() {
    assert!(note_to_hz("H4").is_err());
    assert!(note_to_hz("C").is_err());
    assert!(note_to_hz("").is_err());
}

#[test]
fn note_to_hz_accepts_microtonal_suffix() {
    let hz = note_to_hz("C#4+37").unwrap();
    let base = note_to_hz("C#4").unwrap();
    assert!((hz - base * 2f64.powf(37.0 / 1200.0)).abs() < 1e-6);
}
