use clap::{Parser, Subcommand};

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
    },
    List,
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
    Favorites,
    Try {
        track_id: i64,
    },
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

#[derive(Subcommand)]
enum ShelfCmd {
    Star { variant_id: i64 },
    Prune { track_id: i64 },
    Export { track_id: i64, dir: String },
}

fn main() -> anyhow::Result<()> {
    let cli = Cli::parse();
    match &cli.command {
        Commands::Note { hz } => {
            let n = pitcher_core::notes::hz_to_note(*hz);
            println!("{} {:.2} Hz {:.1} cents off", n.name, hz, n.cents_off);
        }
        Commands::Interval { .. } => {
            anyhow::bail!("not implemented yet");
        }
        Commands::Pitch {
            input,
            output,
            cents,
            formant,
            ..
        } => {
            let req = pitcher_core::engine::ShiftRequest {
                input: input.into(),
                output: output.into(),
                cents: *cents,
                formant: *formant,
                engine: pitcher_core::engine::Engine::Finer,
                pitch_quality: pitcher_core::engine::PitchQuality::Quality,
                section: None,
                output_format: None,
            };
            pitcher_core::engine::shift(&req)?;
        }
        Commands::Detect { track, at } => {
            let reading = pitcher_core::tuner::detect_at(
                std::path::Path::new(track),
                *at,
                250,
                pitcher_core::tuner::Method::Yin,
            )?;
            match reading {
                Some(r) => println!(
                    "{} {:.2} Hz {:.1} cents off",
                    r.note.name, r.hz, r.note.cents_off
                ),
                None => println!("no pitch"),
            }
        }
        Commands::Add { source, title } => {
            let shelf = pitcher_core::shelf::Shelf::open(&cli.db)?;
            let dur = pitcher_core::tuner::duration(std::path::Path::new(source))?;
            let t = if title.is_empty() { "untitled" } else { title };
            let id = shelf.add_track(source, "file", None, t, "", dur, 44100)?;
            println!("added track {id}");
        }
        Commands::List => {
            let shelf = pitcher_core::shelf::Shelf::open(&cli.db)?;
            for t in shelf.list_tracks()? {
                println!("[{}] {} ({} variants)", t.id, t.title, t.variant_count);
            }
        }
        Commands::Explore {
            track_id,
            offset,
            span,
            step,
            outdir,
        } => {
            let shelf = pitcher_core::shelf::Shelf::open(&cli.db)?;
            let track = shelf
                .get_track(*track_id)?
                .ok_or_else(|| anyhow::anyhow!("no track {track_id}"))?;
            std::fs::create_dir_all(outdir)?;
            let mut c = *offset;
            while c <= *offset + *span {
                let stem = std::path::Path::new(&track.source_path)
                    .file_stem()
                    .and_then(|s| s.to_str())
                    .unwrap_or("out");
                let out = std::path::Path::new(outdir).join(format!("{stem}_{c:+}.wav"));
                let req = pitcher_core::engine::ShiftRequest {
                    input: track.source_path.clone().into(),
                    output: out.clone(),
                    cents: c,
                    formant: true,
                    engine: pitcher_core::engine::Engine::Finer,
                    pitch_quality: pitcher_core::engine::PitchQuality::Quality,
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
        }
        Commands::Shelf(_) | Commands::Variant(_) | Commands::Favorites | Commands::Try { .. } => {
            anyhow::bail!("not implemented yet");
        }
    }
    Ok(())
}
