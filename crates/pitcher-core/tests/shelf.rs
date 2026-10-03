use pitcher_core::shelf::Shelf;

fn tmp_db(tag: &str) -> std::path::PathBuf {
    let dir = std::env::temp_dir().join(format!("pitcher-shelf-{tag}-{}", std::process::id()));
    std::fs::create_dir_all(&dir).unwrap();
    let db = dir.join("shelf.sqlite");
    let _ = std::fs::remove_file(&db);
    db
}

#[test]
fn add_and_list_track() {
    let shelf = Shelf::open(tmp_db("add")).unwrap();
    let id = shelf
        .add_track("/music/a.wav", "file", None, "A", "Artist", 12.0, 44100)
        .unwrap();
    assert!(id > 0);
    let tracks = shelf.list_tracks().unwrap();
    assert_eq!(tracks.len(), 1);
    assert_eq!(tracks[0].title, "A");
    assert_eq!(tracks[0].variant_count, 0);
}

#[test]
fn rename_track_updates_title_and_artist() {
    let shelf = Shelf::open(tmp_db("rename")).unwrap();
    let id = shelf
        .add_track("/music/a.wav", "file", None, "Old", "Nobody", 12.0, 44100)
        .unwrap();
    shelf
        .rename_track(id, Some("New"), Some("Somebody"))
        .unwrap();
    let t = shelf.get_track(id).unwrap().unwrap();
    assert_eq!(t.title, "New");
    assert_eq!(t.artist.as_deref(), Some("Somebody"));
}

#[test]
fn rename_track_partial_keeps_other_field() {
    let shelf = Shelf::open(tmp_db("renamep")).unwrap();
    let id = shelf
        .add_track("/music/a.wav", "file", None, "Old", "Nobody", 12.0, 44100)
        .unwrap();
    shelf.rename_track(id, Some("New"), None).unwrap();
    let t = shelf.get_track(id).unwrap().unwrap();
    assert_eq!(t.title, "New");
    assert_eq!(t.artist.as_deref(), Some("Nobody"));
}

#[test]
fn delete_track_with_files_removes_managed_files() {
    let base = std::env::temp_dir().join(format!("pitcher-purge-{}", std::process::id()));
    let data = base.join("data");
    std::fs::create_dir_all(&data).unwrap();
    let shelf = Shelf::open(data.join("shelf.sqlite")).unwrap();

    let src = data.join("imports").join("a.opus");
    std::fs::create_dir_all(src.parent().unwrap()).unwrap();
    std::fs::write(&src, b"audio").unwrap();
    let tid = shelf
        .add_track(src.to_str().unwrap(), "url", None, "A", "", 10.0, 48000)
        .unwrap();
    let vpath = data.join("out").join("a_-100.opus");
    std::fs::create_dir_all(vpath.parent().unwrap()).unwrap();
    std::fs::write(&vpath, b"variant").unwrap();
    shelf
        .add_variant(
            tid,
            -100,
            true,
            "finer",
            "quality",
            None,
            vpath.to_str().unwrap(),
            Some("opus"),
        )
        .unwrap();

    assert!(shelf.delete_track_with_files(tid, &data).unwrap());
    assert!(!src.exists(), "managed source should be removed");
    assert!(!vpath.exists(), "variant file should be removed");
    assert!(shelf.list_tracks().unwrap().is_empty());
}

#[test]
fn delete_track_with_files_keeps_external_source() {
    let base = std::env::temp_dir().join(format!("pitcher-purge-ext-{}", std::process::id()));
    let data = base.join("data");
    let external = base.join("music");
    std::fs::create_dir_all(&data).unwrap();
    std::fs::create_dir_all(&external).unwrap();
    let shelf = Shelf::open(data.join("shelf.sqlite")).unwrap();

    let src = external.join("a.wav");
    std::fs::write(&src, b"audio").unwrap();
    let tid = shelf
        .add_track(src.to_str().unwrap(), "file", None, "A", "", 10.0, 44100)
        .unwrap();

    assert!(shelf.delete_track_with_files(tid, &data).unwrap());
    assert!(src.exists(), "user local file must be kept");
    assert!(shelf.list_tracks().unwrap().is_empty());
}

#[test]
fn delete_track_with_files_missing_returns_false() {
    let shelf = Shelf::open(tmp_db("purgemiss")).unwrap();
    let data = std::env::temp_dir().join(format!("pitcher-purgemiss-{}", std::process::id()));
    assert!(!shelf.delete_track_with_files(999, &data).unwrap());
}

#[test]
fn rename_variant_sets_name() {
    let shelf = Shelf::open(tmp_db("vrename")).unwrap();
    let tid = shelf
        .add_track("/music/a.wav", "file", None, "A", "", 12.0, 44100)
        .unwrap();
    let v = shelf
        .add_variant(
            tid,
            -100,
            true,
            "finer",
            "quality",
            None,
            "/out/a.opus",
            Some("opus"),
        )
        .unwrap();
    shelf.rename_variant(v, "my low version").unwrap();
    let got = shelf.get_variant(v).unwrap().unwrap();
    assert_eq!(got.name.as_deref(), Some("my low version"));
}

#[test]
fn variants_default_to_no_name() {
    let shelf = Shelf::open(tmp_db("vnoname")).unwrap();
    let tid = shelf
        .add_track("/music/a.wav", "file", None, "A", "", 12.0, 44100)
        .unwrap();
    let v = shelf
        .add_variant(
            tid,
            -100,
            true,
            "finer",
            "quality",
            None,
            "/out/a.opus",
            Some("opus"),
        )
        .unwrap();
    let got = shelf.get_variant(v).unwrap().unwrap();
    assert_eq!(got.name, None);
}

#[test]
fn old_db_without_name_column_migrates() {
    use rusqlite::Connection;
    let dir = std::env::temp_dir().join(format!("pitcher-mig-{}", std::process::id()));
    std::fs::create_dir_all(&dir).unwrap();
    let db = dir.join("old.sqlite");
    let _ = std::fs::remove_file(&db);
    let conn = Connection::open(&db).unwrap();
    conn.execute_batch(
        "CREATE TABLE tracks (id INTEGER PRIMARY KEY AUTOINCREMENT, source_path TEXT NOT NULL,
            source_kind TEXT NOT NULL, source_url TEXT, title TEXT NOT NULL, artist TEXT,
            duration_s REAL NOT NULL, sample_rate INTEGER,
            created_at TEXT NOT NULL DEFAULT (datetime('now')));
         CREATE TABLE variants (id INTEGER PRIMARY KEY AUTOINCREMENT,
            track_id INTEGER NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
            cents INTEGER NOT NULL, formant INTEGER NOT NULL, engine TEXT NOT NULL,
            pitch_quality TEXT NOT NULL, section_start REAL, section_end REAL,
            output_path TEXT NOT NULL, output_format TEXT, src_note TEXT, src_hz REAL,
            target_note TEXT, target_hz REAL, favorite INTEGER NOT NULL DEFAULT 0,
            created_at TEXT NOT NULL DEFAULT (datetime('now')));",
    )
    .unwrap();
    drop(conn);

    let shelf = Shelf::open(&db).unwrap();
    let tid = shelf
        .add_track("/music/a.wav", "file", None, "A", "", 12.0, 44100)
        .unwrap();
    let v = shelf
        .add_variant(
            tid,
            -100,
            true,
            "finer",
            "quality",
            None,
            "/out/a.opus",
            Some("opus"),
        )
        .unwrap();
    shelf.rename_variant(v, "migrated").unwrap();
    assert_eq!(
        shelf.get_variant(v).unwrap().unwrap().name.as_deref(),
        Some("migrated")
    );
}

#[test]
fn find_variant_matches_render_settings() {
    let shelf = Shelf::open(tmp_db("findv")).unwrap();
    let tid = shelf
        .add_track("/music/a.wav", "file", None, "A", "", 12.0, 44100)
        .unwrap();
    let v = shelf
        .add_variant_full(
            tid,
            &pitcher_core::model::VariantSpec {
                cents: -600,
                formant: true,
                engine: "finer".into(),
                pitch_quality: "quality".into(),
                section: None,
                output_path: "/out/a.opus".into(),
                output_format: Some("opus".into()),
                src_note: None,
                src_hz: None,
                target_note: None,
                target_hz: None,
            },
        )
        .unwrap();
    let found = shelf
        .find_variant(tid, -600, true, None, Some("opus"))
        .unwrap()
        .unwrap();
    assert_eq!(found.id, v);
    assert!(shelf
        .find_variant(tid, -500, true, None, Some("opus"))
        .unwrap()
        .is_none());
    assert!(shelf
        .find_variant(tid, -600, false, None, Some("opus"))
        .unwrap()
        .is_none());
    assert!(shelf
        .find_variant(tid, -600, true, Some((1.0, 2.0)), Some("opus"))
        .unwrap()
        .is_none());
}

#[test]
fn variants_list_sorted_by_shift_amount() {
    let shelf = Shelf::open(tmp_db("sorted")).unwrap();
    let tid = shelf
        .add_track("/music/a.wav", "file", None, "A", "", 12.0, 44100)
        .unwrap();
    for cents in [200, -100, 0, -300, 100] {
        shelf
            .add_variant(
                tid,
                cents,
                true,
                "finer",
                "quality",
                None,
                "/out/a.opus",
                Some("opus"),
            )
            .unwrap();
    }
    let cents: Vec<i32> = shelf
        .list_variants(tid)
        .unwrap()
        .iter()
        .map(|v| v.cents)
        .collect();
    assert_eq!(cents, vec![-300, -100, 0, 100, 200]);
}

#[test]
fn add_variant_and_count() {
    let shelf = Shelf::open(tmp_db("var")).unwrap();
    let tid = shelf
        .add_track("/music/a.wav", "file", None, "A", "Artist", 12.0, 44100)
        .unwrap();
    let v1 = shelf
        .add_variant(
            tid,
            -100,
            true,
            "finer",
            "quality",
            None,
            "/out/a-c.wav",
            Some("wav"),
        )
        .unwrap();
    shelf
        .add_variant(
            tid,
            700,
            false,
            "finer",
            "quality",
            None,
            "/out/a-g.wav",
            Some("wav"),
        )
        .unwrap();
    let tracks = shelf.list_tracks().unwrap();
    assert_eq!(tracks[0].variant_count, 2);
    let variants = shelf.list_variants(tid).unwrap();
    assert_eq!(variants.len(), 2);
    assert!(variants.iter().any(|v| v.id == v1 && v.cents == -100));
}

#[test]
fn star_and_favorites() {
    let shelf = Shelf::open(tmp_db("star")).unwrap();
    let tid = shelf
        .add_track("/music/a.wav", "file", None, "A", "Artist", 12.0, 44100)
        .unwrap();
    let v = shelf
        .add_variant(
            tid,
            -100,
            true,
            "finer",
            "quality",
            None,
            "/out/a.wav",
            None,
        )
        .unwrap();
    assert!(shelf.list_favorites().unwrap().is_empty());
    shelf.set_favorite(v, true).unwrap();
    let favs = shelf.list_favorites().unwrap();
    assert_eq!(favs.len(), 1);
    assert_eq!(favs[0].id, v);
    shelf.set_favorite(v, false).unwrap();
    assert!(shelf.list_favorites().unwrap().is_empty());
}

#[test]
fn delete_variant() {
    let shelf = Shelf::open(tmp_db("delv")).unwrap();
    let tid = shelf
        .add_track("/music/a.wav", "file", None, "A", "Artist", 12.0, 44100)
        .unwrap();
    let v = shelf
        .add_variant(
            tid,
            -100,
            true,
            "finer",
            "quality",
            None,
            "/out/a.wav",
            None,
        )
        .unwrap();
    shelf.delete_variant(v).unwrap();
    assert!(shelf.list_variants(tid).unwrap().is_empty());
}

#[test]
fn delete_track_cascades() {
    let shelf = Shelf::open(tmp_db("delc")).unwrap();
    let tid = shelf
        .add_track("/music/a.wav", "file", None, "A", "Artist", 12.0, 44100)
        .unwrap();
    shelf
        .add_variant(
            tid,
            -100,
            true,
            "finer",
            "quality",
            None,
            "/out/a.wav",
            None,
        )
        .unwrap();
    shelf
        .add_variant(
            tid,
            700,
            false,
            "finer",
            "quality",
            None,
            "/out/b.wav",
            None,
        )
        .unwrap();
    shelf.delete_track(tid).unwrap();
    assert!(shelf.list_tracks().unwrap().is_empty());
    assert!(shelf.list_variants(tid).unwrap().is_empty());
}

#[test]
fn find_track_by_source() {
    let shelf = Shelf::open(tmp_db("find")).unwrap();
    let tid = shelf
        .add_track("/music/a.wav", "file", None, "A", "Artist", 12.0, 44100)
        .unwrap();
    let found = shelf.find_track_by_source("/music/a.wav").unwrap();
    assert_eq!(found, Some(tid));
    assert_eq!(shelf.find_track_by_source("/music/none.wav").unwrap(), None);
}

#[test]
fn variant_stores_section_and_note_metadata() {
    let shelf = Shelf::open(tmp_db("meta")).unwrap();
    let tid = shelf
        .add_track("/music/a.wav", "file", None, "A", "Artist", 12.0, 44100)
        .unwrap();
    let v = shelf
        .add_variant_full(
            tid,
            &pitcher_core::model::VariantSpec {
                cents: -100,
                formant: true,
                engine: "finer".into(),
                pitch_quality: "quality".into(),
                section: Some((1.0, 2.0)),
                output_path: "/out/a.wav".into(),
                output_format: Some("wav".into()),
                src_note: Some("C#4".into()),
                src_hz: Some(277.18),
                target_note: Some("C4".into()),
                target_hz: Some(261.63),
            },
        )
        .unwrap();
    let v = shelf.get_variant(v).unwrap().unwrap();
    assert_eq!(v.src_note.as_deref(), Some("C#4"));
    assert_eq!(v.section_start, Some(1.0));
    assert_eq!(v.section_end, Some(2.0));
}
