-- Supabase Storage bucket for audio content (upload via Studio)
-- Uses base columns from supabase/postgres init-scripts; storage-api adds limits on first start.

INSERT INTO storage.buckets (id, name)
VALUES ('content', 'content')
ON CONFLICT (id) DO NOTHING;
