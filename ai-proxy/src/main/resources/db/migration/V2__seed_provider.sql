INSERT INTO ai_providers (id, base_url, api_key_enc, display_name, enabled)
VALUES ('default', 'https://api.example.com', 'REPLACE_AFTER_BOOT', 'Default Provider', false)
ON CONFLICT DO NOTHING;

INSERT INTO model_configs (id, provider_id, display_name, context_window, supports_streaming, enabled)
VALUES
    ('gpt-4o', 'default', 'GPT-4o', 128000, true, false),
    ('gpt-4o-mini', 'default', 'GPT-4o Mini', 128000, true, false)
ON CONFLICT DO NOTHING;
