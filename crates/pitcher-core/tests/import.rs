use pitcher_core::import::{
    detect_kind, is_url, js_runtime_args, transcode_archive, yt_dlp_args, yt_dlp_command,
};

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

#[test]
fn yt_dlp_command_prefers_env_then_path() {
    std::env::remove_var("PITCHER_YTDLP");
    let cmd = yt_dlp_command();
    let program = cmd.get_program().to_string_lossy().to_string();
    assert!(
        program.contains("yt-dlp") || program.ends_with("yt_dlp"),
        "unexpected program: {program}"
    );
}

#[test]
fn yt_dlp_args_extract_lossless_intermediate() {
    let args = yt_dlp_args();
    let joined = args.join(" ");
    assert!(joined.contains("bestaudio"), "args: {joined}");
    assert!(
        joined.contains("--audio-format flac"),
        "yt-dlp should extract a lossless intermediate; transcode happens after, args: {joined}"
    );
    assert!(joined.contains("--no-playlist"), "args: {joined}");
    assert!(joined.contains("after_move:filepath"), "args: {joined}");
}

#[test]
fn yt_dlp_args_include_js_runtime_when_available() {
    let args = yt_dlp_args();
    let joined = args.join(" ");
    if which_node().is_some() {
        assert!(
            joined.contains("--js-runtimes"),
            "expected --js-runtimes when node is present: {joined}"
        );
    }
}

#[test]
fn js_runtime_args_are_pairs() {
    let args = js_runtime_args(Some("node"), None);
    assert_eq!(args, vec!["--js-runtimes", "node"]);
    let args2 = js_runtime_args(None, Some("deno"));
    assert_eq!(args2, vec!["--js-runtimes", "deno"]);
    let args3 = js_runtime_args(None, None);
    assert!(args3.is_empty());
}

#[test]
fn transcode_archive_produces_opus_from_flac_intermediate() {
    if !have("ffmpeg") {
        return;
    }
    let dir = tmp_dir("transcode");
    let flac = dir.join("source.flac");
    let status = std::process::Command::new("ffmpeg")
        .args(["-hide_banner", "-loglevel", "error", "-y"])
        .args(["-f", "lavfi", "-i"])
        .arg("anoisesrc=duration=3:sample_rate=44100:amplitude=0.4")
        .args(["-c:a", "flac"])
        .arg(&flac)
        .status()
        .unwrap();
    assert!(status.success());

    std::env::remove_var("PITCHER_ARCHIVE_FORMAT");
    let out = transcode_archive(&flac, &dir);
    assert_eq!(out.extension().unwrap(), "opus", "got {}", out.display());
    assert!(out.exists());
    assert!(
        !flac.exists(),
        "lossless intermediate should be removed after transcode"
    );
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
    let dir = std::env::temp_dir().join(format!("pitcher-import-{tag}-{}", std::process::id()));
    std::fs::create_dir_all(&dir).unwrap();
    dir
}

fn which_node() -> Option<()> {
    let ok = std::process::Command::new("sh")
        .arg("-c")
        .arg("command -v node")
        .status()
        .map(|s| s.success())
        .unwrap_or(false);
    ok.then_some(())
}
