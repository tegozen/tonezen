-- Static track waveform metadata for expanded player seekbars.

ALTER TABLE track_files ADD COLUMN IF NOT EXISTS waveform_peaks JSONB;
