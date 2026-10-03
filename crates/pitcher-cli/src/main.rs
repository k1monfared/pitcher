use std::path::Path;

use clap::{Parser, Subcommand};

use pitcher_core::engine::{Engine, PitchQuality, ShiftRequest};
use pitcher_core::notes::{cents_between_hz, hz_to_note, note_to_hz};
use pitcher_core::shelf::Shelf;
use pitcher_core::tuner::{detect_at, duration, Method};

#[derive(Parser)]
#[command(name = "pitcher", version, about = "FOSS pitch-shifting toolkit")]
struct Cli {
    #[arg(long, global = true, default_value = "data/shelf.sqlite")]
    db: String,
    #[command(subcommand)]
    command: Commands,
}

#[derive(Subcommand)]
enum Commands {
    Pitch {
        input: String,
        output: String,
        #[arg(long, default_value_t = 0, allow_hyphen_values = true)]
        cents: i32,
        #[arg(long)]
        to_note: Option<String>,
        #[arg(long)]
        from_note: Option<String>,
        #[arg(long)]
        formant: bool,
    },
    Detect {
        track: String,
        #[arg(long, default_value_t = 0.0)]
        at: f64,
    },
    Note {
        #[arg(long)]
        hz: f64,
    },
    Interval {
        source: Option<String>,
        target: Option<String>,
        #[arg(long)]
        source_hz: Option<f64>,
        #[arg(long)]
        target_note: Option<String>,
    },
    Add {
        source: String,
        #[arg(long, default_value = "")]
        title: String,
        #[arg(long, default_value = "")]
        artist: String,
    },
    List,
    Favorites,
    Explore {
        track_id: i64,
        #[arg(long, default_value_t = -200, allow_hyphen_values = true)]
        offset: i32,
        #[arg(long, default_value_t = 200)]
        span: i32,
        #[arg(long, default_value_t = 100)]
        step: i32,
        #[arg(long, default_value = "data/out")]
        outdir: String,
    },
    #[command(subcommand)]
    Shelf(ShelfCmd),
    #[command(subcommand)]
    Variant(VariantCmd),
    Try {
        track_id: i64,
        #[arg(long, default_value = "data/out")]
        outdir: String,
        #[arg(long)]
        no_play: bool,
        #[arg(long)]
        formant: bool,
    },
}

#[derive(Subcommand)]
enum ShelfCmd {
    Star { variant_id: i64 },
    Unstar { variant_id: i64 },
    Prune { track_id: i64 },
    Export { track_id: i64, dir: String },
}

#[derive(Subcommand)]
enum VariantCmd {
    Add {
        track_id: i64,
        #[arg(long, allow_hyphen_values = true)]
        cents: i32,
        #[arg(long)]
        path: String,
        #[arg(long)]
        formant: bool,
    },
}

fn resolve_cents(
    cents: i32,
    to_note: Option<&str>,
    from_note: Option<&str>,
) -> anyhow::Result<i32> {
    match (to_note, from_note) {
        (Some(target), Some(src)) => {
            Ok(cents_between_hz(note_to_hz(src)?, note_to_hz(target)?).round() as i32)
        }
        (Some(target), None) => Ok(cents_between_hz(440.0, note_to_hz(target)?).round() as i32),
        _ => Ok(cents),
    }
}

fn main() -> anyhow::Result<()> {
    let cli = Cli::parse();
    match &cli.command {
        Commands::Note { hz } => {
            let n = hz_to_note(*hz);
            println!("{} {:.2} Hz {:+.1} cents off", n.name, hz, n.cents_off);
        }
        Commands::Interval {
            source,
            target,
            source_hz,
            target_note,
        } => {
            let cents = if let (Some(sh), Some(tn)) = (source_hz, target_note) {
                cents_between_hz(*sh, note_to_hz(tn)?)
            } else if let (Some(s), Some(t)) = (source, target) {
                cents_between_hz(note_to_hz(s)?, note_to_hz(t)?)
            } else {
                anyhow::bail!("provide either <source> <target> or --source-hz/--target-note");
            };
            println!("{cents:.0}");
        }
        Commands::Pitch {
            input,
            output,
            cents,
            to_note,
            from_note,
            formant,
        } => {
            let resolved = match (to_note.as_deref(), from_note.as_deref()) {
                (Some(target), Some(src)) => {
                    cents_between_hz(note_to_hz(src)?, note_to_hz(target)?).round() as i32
                }
                (Some(target), None) => {
                    let reading =
                        detect_at(Path::new(input), 0.5, 400, Method::Yin)?.ok_or_else(|| {
                            anyhow::anyhow!(
                                "could not detect a source pitch in {input}; pass --from-note"
                            )
                        })?;
                    cents_between_hz(reading.hz, note_to_hz(target)?).round() as i32
                }
                _ => resolve_cents(*cents, None, None)?,
            };
            let req = ShiftRequest {
                input: input.into(),
                output: output.into(),
                cents: resolved,
                formant: *formant,
                engine: Engine::Finer,
                pitch_quality: PitchQuality::Quality,
                section: None,
                output_format: None,
            };
            pitcher_core::engine::shift(&req)?;
            println!("shifted {resolved:+} cents -> {output}");
        }
        Commands::Detect { track, at } => {
            let reading = detect_at(Path::new(track), *at, 250, Method::Yin)?;
            match reading {
                Some(r) => println!(
                    "{} {:.2} Hz {:+.1} cents off (confidence {:.2})",
                    r.note.name, r.hz, r.note.cents_off, r.confidence
                ),
                None => println!("no pitch"),
            }
        }
        Commands::Add {
            source,
            title,
            artist,
        } => {
            let shelf = Shelf::open(&cli.db)?;
            let dur = duration(Path::new(source))?;
            let t = if title.is_empty() { "untitled" } else { title };
            let id = shelf.add_track(source, "file", None, t, artist, dur, 44100)?;
            println!("added track {id}");
        }
        Commands::List => {
            let shelf = Shelf::open(&cli.db)?;
            for t in shelf.list_tracks()? {
                println!("[{}] {} ({} variants)", t.id, t.title, t.variant_count);
            }
        }
        Commands::Favorites => {
            let shelf = Shelf::open(&cli.db)?;
            for v in shelf.list_favorites()? {
                println!("{} -> {} ({:+} cents)", v.id, v.output_path, v.cents);
            }
        }
        Commands::Explore {
            track_id,
            offset,
            span,
            step,
            outdir,
        } => {
            let shelf = Shelf::open(&cli.db)?;
            let track = shelf
                .get_track(*track_id)?
                .ok_or_else(|| anyhow::anyhow!("no track {track_id}"))?;
            std::fs::create_dir_all(outdir)?;
            let stem = Path::new(&track.source_path)
                .file_stem()
                .and_then(|s| s.to_str())
                .unwrap_or("out");
            let mut c = *offset;
            while c <= *offset + *span {
                let out = Path::new(outdir).join(format!("{stem}_{c:+}.wav"));
                let req = ShiftRequest {
                    input: track.source_path.clone().into(),
                    output: out.clone(),
                    cents: c,
                    formant: true,
                    engine: Engine::Finer,
                    pitch_quality: PitchQuality::Quality,
                    section: None,
                    output_format: Some("wav".into()),
                };
                pitcher_core::engine::shift(&req)?;
                shelf.add_variant(
                    *track_id,
                    c,
                    true,
                    "finer",
                    "quality",
                    None,
                    out.to_str().unwrap(),
                    Some("wav"),
                )?;
                c += *step;
            }
            println!("generated variants into {}", outdir);
        }
        Commands::Shelf(cmd) => run_shelf(&cli.db, cmd)?,
        Commands::Variant(VariantCmd::Add {
            track_id,
            cents,
            path,
            formant,
        }) => {
            let shelf = Shelf::open(&cli.db)?;
            let id = shelf.add_variant(
                *track_id, *cents, *formant, "finer", "quality", None, path, None,
            )?;
            println!("added variant {id}");
        }
        Commands::Try {
            track_id,
            outdir,
            no_play,
            formant,
        } => run_try(&cli.db, *track_id, outdir, *no_play, *formant)?,
    }
    Ok(())
}

fn run_try(
    db: &str,
    track_id: i64,
    outdir: &str,
    no_play: bool,
    formant: bool,
) -> anyhow::Result<()> {
    use std::io::{BufRead, Write};

    let shelf = Shelf::open(db)?;
    let track = shelf
        .get_track(track_id)?
        .ok_or_else(|| anyhow::anyhow!("no track {track_id}"))?;
    std::fs::create_dir_all(outdir)?;

    println!("track: {} ({:.1}s)", track.title, track.duration_s);
    println!("enter cents (e.g. -100), 'k' to keep last, 'q' to quit");

    let stdin = std::io::stdin();
    let mut last: Option<i32> = None;

    loop {
        print!("> ");
        std::io::stdout().flush()?;
        let mut line = String::new();
        if stdin.lock().read_line(&mut line)? == 0 {
            break;
        }
        let cmd = line.trim();
        if cmd.is_empty() {
            continue;
        }
        if cmd == "q" {
            break;
        }
        if cmd == "k" {
            match last {
                Some(cents) => {
                    let stem = Path::new(&track.source_path)
                        .file_stem()
                        .and_then(|s| s.to_str())
                        .unwrap_or("out");
                    let out = Path::new(outdir).join(format!("{stem}_{cents:+}.wav"));
                    let req = ShiftRequest {
                        input: track.source_path.clone().into(),
                        output: out.clone(),
                        cents,
                        formant,
                        engine: Engine::Finer,
                        pitch_quality: PitchQuality::Quality,
                        section: None,
                        output_format: Some("wav".into()),
                    };
                    pitcher_core::engine::shift(&req)?;
                    let id = shelf.add_variant(
                        track_id,
                        cents,
                        formant,
                        "finer",
                        "quality",
                        None,
                        out.to_str().unwrap(),
                        Some("wav"),
                    )?;
                    println!("kept variant {id} at {cents:+} cents");
                }
                None => println!("nothing to keep yet"),
            }
            continue;
        }

        match cmd.parse::<i32>() {
            Ok(cents) => {
                last = Some(cents);
                let stem = Path::new(&track.source_path)
                    .file_stem()
                    .and_then(|s| s.to_str())
                    .unwrap_or("preview");
                let preview = Path::new(outdir).join(format!(".preview_{stem}.wav"));
                let req = ShiftRequest {
                    input: track.source_path.clone().into(),
                    output: preview.clone(),
                    cents,
                    formant,
                    engine: Engine::Finer,
                    pitch_quality: PitchQuality::Speed,
                    section: None,
                    output_format: Some("wav".into()),
                };
                pitcher_core::engine::shift(&req)?;
                println!("preview {cents:+} cents -> {}", preview.display());
                if !no_play {
                    play_file(&preview);
                }
            }
            Err(_) => println!("enter an integer number of cents, or k/q"),
        }
    }
    Ok(())
}

fn play_file(path: &Path) {
    let _ = std::process::Command::new("ffplay")
        .args(["-nodisp", "-autoexit", "-loglevel", "error"])
        .arg(path)
        .status();
}

fn run_shelf(db: &str, cmd: &ShelfCmd) -> anyhow::Result<()> {
    let shelf = Shelf::open(db)?;
    match cmd {
        ShelfCmd::Star { variant_id } => {
            shelf.set_favorite(*variant_id, true)?;
            println!("starred variant {variant_id}");
        }
        ShelfCmd::Unstar { variant_id } => {
            shelf.set_favorite(*variant_id, false)?;
            println!("unstarred variant {variant_id}");
        }
        ShelfCmd::Prune { track_id } => {
            for v in shelf.list_variants(*track_id)? {
                shelf.delete_variant(v.id)?;
            }
            println!("pruned variants for track {track_id}");
        }
        ShelfCmd::Export { track_id, dir } => {
            let variants = shelf.list_variants(*track_id)?;
            let favorites: Vec<_> = variants.iter().filter(|v| v.favorite).collect();
            let to_export: Vec<_> = if favorites.is_empty() {
                variants.iter().collect()
            } else {
                favorites
            };
            std::fs::create_dir_all(dir)?;
            for v in to_export {
                let name = Path::new(&v.output_path)
                    .file_name()
                    .and_then(|s| s.to_str())
                    .unwrap_or("variant.wav");
                let dest = Path::new(dir).join(name);
                std::fs::copy(&v.output_path, &dest)?;
                println!("exported {}", dest.display());
            }
        }
    }
    Ok(())
}
