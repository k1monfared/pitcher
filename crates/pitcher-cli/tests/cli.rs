use std::path::PathBuf;
use std::process::Command;

fn bin() -> PathBuf {
    let mut p = PathBuf::from(env!("CARGO_BIN_EXE_pitcher"));
    p.pop();
    p.push("pitcher");
    p
}

fn tmp_dir(tag: &str) -> PathBuf {
    let dir = std::env::temp_dir().join(format!("pitcher-cli-{tag}-{}", std::process::id()));
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

fn make_tone(path: &PathBuf, hz: f64, secs: f64) {
    let status = Command::new("ffmpeg")
        .args(["-hide_banner", "-loglevel", "error", "-y"])
        .args(["-f", "lavfi", "-i"])
        .arg(format!(
            "sine=frequency={hz}:duration={secs}:sample_rate=44100"
        ))
        .arg(path)
        .status()
        .unwrap();
    assert!(status.success());
}

fn run(args: &[&str]) -> std::process::Output {
    Command::new(bin()).args(args).output().unwrap()
}

#[test]
fn help_works() {
    let out = run(&["--help"]);
    assert!(out.status.success());
    let s = String::from_utf8_lossy(&out.stdout);
    for cmd in ["pitch", "detect", "add", "list", "explore", "shelf", "try"] {
        assert!(s.contains(cmd), "help missing {cmd}:\n{s}");
    }
}

#[test]
fn version_works() {
    let out = run(&["--version"]);
    assert!(out.status.success());
    assert!(String::from_utf8_lossy(&out.stdout).contains("pitcher"));
}

#[test]
fn detect_note_subcommand_works() {
    let out = run(&["note", "--hz", "440"]);
    assert!(out.status.success());
    let s = String::from_utf8_lossy(&out.stdout);
    assert!(s.contains("A4"), "output: {s}");
    assert!(s.contains("440"), "output: {s}");
}

#[test]
fn detect_interval_subcommand_works() {
    let out = run(&["interval", "C#4", "C4"]);
    assert!(out.status.success());
    let s = String::from_utf8_lossy(&out.stdout);
    assert!(s.contains("-100"), "output: {s}");
}

#[test]
fn pitch_shifts_a_file() {
    if !have("ffmpeg") {
        return;
    }
    let dir = tmp_dir("pitch");
    let input = dir.join("in.wav");
    let output = dir.join("out.wav");
    make_tone(&input, 440.0, 1.0);

    let out = run(&[
        "pitch",
        input.to_str().unwrap(),
        output.to_str().unwrap(),
        "--cents",
        "-100",
    ]);
    assert!(
        out.status.success(),
        "stderr: {}",
        String::from_utf8_lossy(&out.stderr)
    );
    assert!(output.exists());
}

#[test]
fn detect_at_file() {
    if !have("ffmpeg") {
        return;
    }
    let dir = tmp_dir("detect");
    let input = dir.join("in.wav");
    make_tone(&input, 440.0, 2.0);

    let out = run(&["detect", input.to_str().unwrap(), "--at", "1.0"]);
    assert!(
        out.status.success(),
        "stderr: {}",
        String::from_utf8_lossy(&out.stderr)
    );
    assert!(String::from_utf8_lossy(&out.stdout).contains("A4"));
}

#[test]
fn add_and_list_roundtrip() {
    if !have("ffmpeg") {
        return;
    }
    let dir = tmp_dir("shelf");
    let db = dir.join("shelf.sqlite");
    let input = dir.join("in.wav");
    make_tone(&input, 440.0, 1.0);

    let out = run(&[
        "--db",
        db.to_str().unwrap(),
        "add",
        input.to_str().unwrap(),
        "--title",
        "Test Tone",
    ]);
    assert!(
        out.status.success(),
        "stderr: {}",
        String::from_utf8_lossy(&out.stderr)
    );

    let out = run(&["--db", db.to_str().unwrap(), "list"]);
    assert!(out.status.success());
    assert!(String::from_utf8_lossy(&out.stdout).contains("Test Tone"));
}

#[test]
fn explore_generates_variants() {
    if !have("ffmpeg") {
        return;
    }
    let dir = tmp_dir("explore");
    let db = dir.join("shelf.sqlite");
    let outdir = dir.join("out");
    let input = dir.join("in.wav");
    make_tone(&input, 440.0, 1.0);

    let add = run(&[
        "--db",
        db.to_str().unwrap(),
        "add",
        input.to_str().unwrap(),
        "--title",
        "Tone",
    ]);
    assert!(add.status.success());

    let out = run(&[
        "--db",
        db.to_str().unwrap(),
        "explore",
        "1",
        "--offset",
        "-200",
        "--span",
        "200",
        "--step",
        "100",
        "--outdir",
        outdir.to_str().unwrap(),
    ]);
    assert!(
        out.status.success(),
        "stderr: {}",
        String::from_utf8_lossy(&out.stderr)
    );

    let list = run(&["--db", db.to_str().unwrap(), "list"]);
    let s = String::from_utf8_lossy(&list.stdout);
    assert!(s.contains("3"), "expected 3 variants:\n{s}");
}

#[test]
fn shelf_star_marks_favorite() {
    if !have("ffmpeg") {
        return;
    }
    let dir = tmp_dir("star");
    let db = dir.join("shelf.sqlite");
    let input = dir.join("in.wav");
    make_tone(&input, 440.0, 1.0);

    assert!(run(&[
        "--db",
        db.to_str().unwrap(),
        "add",
        input.to_str().unwrap(),
        "--title",
        "T"
    ])
    .status
    .success());
    let out = run(&[
        "--db",
        db.to_str().unwrap(),
        "pitch",
        input.to_str().unwrap(),
        dir.join("o.wav").to_str().unwrap(),
        "--cents",
        "-100",
    ]);
    assert!(out.status.success());

    let add = run(&[
        "--db",
        db.to_str().unwrap(),
        "variant",
        "add",
        "1",
        "--cents",
        "-100",
        "--path",
        dir.join("o.wav").to_str().unwrap(),
    ]);
    assert!(
        add.status.success(),
        "stderr: {}",
        String::from_utf8_lossy(&add.stderr)
    );

    let star = run(&["--db", db.to_str().unwrap(), "shelf", "star", "1"]);
    assert!(
        star.status.success(),
        "stderr: {}",
        String::from_utf8_lossy(&star.stderr)
    );

    let favs = run(&["--db", db.to_str().unwrap(), "favorites"]);
    assert!(favs.status.success());
    assert!(String::from_utf8_lossy(&favs.stdout).contains("1"));
}

#[test]
fn shelf_prune_removes_variants() {
    if !have("ffmpeg") {
        return;
    }
    let dir = tmp_dir("prune");
    let db = dir.join("shelf.sqlite");
    let input = dir.join("in.wav");
    make_tone(&input, 440.0, 1.0);

    assert!(run(&[
        "--db",
        db.to_str().unwrap(),
        "add",
        input.to_str().unwrap(),
        "--title",
        "T"
    ])
    .status
    .success());
    let add = run(&[
        "--db",
        db.to_str().unwrap(),
        "variant",
        "add",
        "1",
        "--cents",
        "-100",
        "--path",
        dir.join("o.wav").to_str().unwrap(),
    ]);
    assert!(add.status.success());
    let prune = run(&["--db", db.to_str().unwrap(), "shelf", "prune", "1"]);
    assert!(
        prune.status.success(),
        "stderr: {}",
        String::from_utf8_lossy(&prune.stderr)
    );
    let list = run(&["--db", db.to_str().unwrap(), "list"]);
    assert!(String::from_utf8_lossy(&list.stdout).contains("(0 variants)"));
}

#[test]
fn shelf_export_copies_favorites() {
    if !have("ffmpeg") {
        return;
    }
    let dir = tmp_dir("export");
    let db = dir.join("shelf.sqlite");
    let input = dir.join("in.wav");
    let variants = dir.join("var");
    std::fs::create_dir_all(&variants).unwrap();
    let vpath = variants.join("o.wav");
    make_tone(&input, 440.0, 1.0);
    make_tone(&vpath, 415.3, 1.0);

    assert!(run(&[
        "--db",
        db.to_str().unwrap(),
        "add",
        input.to_str().unwrap(),
        "--title",
        "T"
    ])
    .status
    .success());
    let add = run(&[
        "--db",
        db.to_str().unwrap(),
        "variant",
        "add",
        "1",
        "--cents",
        "-100",
        "--path",
        vpath.to_str().unwrap(),
    ]);
    assert!(add.status.success());
    assert!(run(&["--db", db.to_str().unwrap(), "shelf", "star", "1"])
        .status
        .success());

    let exp = run(&[
        "--db",
        db.to_str().unwrap(),
        "shelf",
        "export",
        "1",
        dir.join("dump").to_str().unwrap(),
    ]);
    assert!(
        exp.status.success(),
        "stderr: {}",
        String::from_utf8_lossy(&exp.stderr)
    );
    let count = std::fs::read_dir(dir.join("dump")).unwrap().count();
    assert!(count >= 1, "expected exported files, got {count}");
}

#[test]
fn shift_to_target_note() {
    if !have("ffmpeg") {
        return;
    }
    let dir = tmp_dir("target");
    let input = dir.join("in.wav");
    let output = dir.join("out.wav");
    make_tone(&input, 277.1826309768721, 1.0);

    let out = run(&[
        "pitch",
        input.to_str().unwrap(),
        output.to_str().unwrap(),
        "--to-note",
        "C4",
        "--formant",
    ]);
    assert!(
        out.status.success(),
        "stderr: {}",
        String::from_utf8_lossy(&out.stderr)
    );
    let s = String::from_utf8_lossy(&out.stdout);
    assert!(s.contains("-100") || s.contains("C4"), "output: {s}");
}

#[test]
fn manual_note_interval() {
    let out = run(&["interval", "--source-hz", "277.18", "--target-note", "G5"]);
    assert!(
        out.status.success(),
        "stderr: {}",
        String::from_utf8_lossy(&out.stderr)
    );
    let s = String::from_utf8_lossy(&out.stdout);
    let cents: f64 = s.split_whitespace().next().unwrap().parse().unwrap();
    assert!((cents - 1800.0).abs() < 3.0, "got {cents}");
}
