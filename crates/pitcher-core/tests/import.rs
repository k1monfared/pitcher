use pitcher_core::import::{detect_kind, is_url};

#[test]
fn url_detection() {
    assert!(is_url("https://youtube.com/watch?v=x"));
    assert!(is_url("http://example.com/a.mp3"));
    assert!(!is_url("/music/a.wav"));
    assert!(!is_url("relative/file.flac"));
}

#[test]
fn kind_detection() {
    assert_eq!(detect_kind("/music/a.wav"), "file");
    assert_eq!(detect_kind("https://www.youtube.com/watch?v=x"), "youtube");
    assert_eq!(detect_kind("https://youtu.be/x"), "youtube");
    assert_eq!(
        detect_kind("https://soundcloud.com/artist/track"),
        "soundcloud"
    );
    assert_eq!(detect_kind("https://open.spotify.com/track/x"), "spotify");
    assert_eq!(detect_kind("https://example.com/x"), "url");
}
