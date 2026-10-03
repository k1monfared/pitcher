use std::path::{Path, PathBuf};
use std::process::Command;

pub fn is_url(source: &str) -> bool {
    source.starts_with("http://") || source.starts_with("https://")
}

pub fn detect_kind(source: &str) -> &'static str {
    if !is_url(source) {
        return "file";
    }
    if source.contains("youtube.com") || source.contains("youtu.be") {
        "youtube"
    } else if source.contains("soundcloud.com") {
        "soundcloud"
    } else if source.contains("spotify.com") {
        "spotify"
    } else {
        "url"
    }
}

pub fn yt_dlp_command() -> Command {
    unimplemented!()
}

pub fn yt_dlp_args() -> Vec<String> {
    unimplemented!()
}

pub fn js_runtime_args(_node: Option<&str>, _deno: Option<&str>) -> Vec<String> {
    unimplemented!()
}

pub fn download(url: &str, out_dir: &Path) -> anyhow::Result<PathBuf> {
    std::fs::create_dir_all(out_dir)?;
    let template = out_dir.join("%(title)s.%(ext)s");
    let output = Command::new("yt-dlp")
        .args(["-x", "--audio-format", "wav", "-o"])
        .arg(&template)
        .args(["--print", "after_move:filepath"])
        .arg(url)
        .output()
        .map_err(|e| anyhow::anyhow!("failed to run yt-dlp: {e}"))?;

    if !output.status.success() {
        anyhow::bail!(
            "yt-dlp failed: {}",
            String::from_utf8_lossy(&output.stderr).trim()
        );
    }

    let path = String::from_utf8_lossy(&output.stdout)
        .lines()
        .last()
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty())
        .ok_or_else(|| anyhow::anyhow!("yt-dlp did not report an output path"))?;
    Ok(PathBuf::from(path))
}
