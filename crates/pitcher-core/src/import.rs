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

fn which(cmd: &str) -> Option<PathBuf> {
    let out = Command::new("sh")
        .arg("-c")
        .arg(format!("command -v {cmd}"))
        .output()
        .ok()?;
    if !out.status.success() {
        return None;
    }
    let s = String::from_utf8_lossy(&out.stdout).trim().to_string();
    (!s.is_empty()).then(|| PathBuf::from(s))
}

fn home_yt_dlp() -> Option<PathBuf> {
    let home = std::env::var_os("HOME")?;
    let candidate = PathBuf::from(home).join(".local/bin/yt-dlp");
    candidate.is_file().then_some(candidate)
}

pub fn yt_dlp_command() -> Command {
    if let Ok(explicit) = std::env::var("PITCHER_YTDLP") {
        if !explicit.is_empty() {
            return Command::new(explicit);
        }
    }
    if let Some(p) = home_yt_dlp() {
        return Command::new(p);
    }
    if let Some(p) = which("yt-dlp") {
        return Command::new(p);
    }
    Command::new("yt-dlp")
}

pub fn js_runtime_args(node: Option<&str>, deno: Option<&str>) -> Vec<String> {
    match (node, deno) {
        (Some(n), _) => vec!["--js-runtimes".into(), n.into()],
        (None, Some(d)) => vec!["--js-runtimes".into(), d.into()],
        (None, None) => Vec::new(),
    }
}

pub fn yt_dlp_args() -> Vec<String> {
    let mut args: Vec<String> = vec![
        "-f".into(),
        "bestaudio/best".into(),
        "--no-playlist".into(),
        "-x".into(),
        "--audio-format".into(),
        "wav".into(),
        "--print".into(),
        "after_move:filepath".into(),
    ];
    let node = which("node").map(|_| "node".to_string());
    let deno = which("deno").map(|_| "deno".to_string());
    args.extend(js_runtime_args(node.as_deref(), deno.as_deref()));
    args
}

pub fn download(url: &str, out_dir: &Path) -> anyhow::Result<PathBuf> {
    std::fs::create_dir_all(out_dir)?;
    let template = out_dir.join("%(title)s.%(ext)s");

    let mut cmd = yt_dlp_command();
    cmd.args(yt_dlp_args());
    cmd.args(["-o"]).arg(&template).arg(url);

    let output = cmd
        .output()
        .map_err(|e| anyhow::anyhow!("failed to run yt-dlp: {e}"))?;

    if !output.status.success() {
        let stderr = String::from_utf8_lossy(&output.stderr);
        let mut msg = format!("yt-dlp failed: {}", stderr.trim());
        if stderr.contains("Precondition check failed")
            || stderr.contains("Requested format is not available")
            || stderr.contains("Unable to download API page")
        {
            msg.push_str(
                "\n\nhint: this usually means yt-dlp is out of date. Update it with \
                 `pip install --user -U yt-dlp` (or your package manager) and retry.",
            );
        }
        anyhow::bail!(msg);
    }

    let path = String::from_utf8_lossy(&output.stdout)
        .lines()
        .rev()
        .map(|s| s.trim().to_string())
        .find(|s| !s.is_empty() && Path::new(s).is_file())
        .ok_or_else(|| {
            anyhow::anyhow!(
                "yt-dlp did not report an output path. stderr:\n{}",
                String::from_utf8_lossy(&output.stderr).trim()
            )
        })?;
    Ok(PathBuf::from(path))
}

pub fn yt_dlp_version() -> Option<String> {
    let out = yt_dlp_command().arg("--version").output().ok()?;
    if !out.status.success() {
        return None;
    }
    Some(String::from_utf8_lossy(&out.stdout).trim().to_string())
}
