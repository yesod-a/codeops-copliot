from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    ai_enabled: bool = False
    ai_api_key: str = ""
    ai_base_url: str = "https://api.openai.com/v1"
    ai_model: str = "gpt-4o-mini"
    ai_temperature: float = 0.1
    ai_timeout_seconds: float = 600.0
    ai_provider_retries: int = 2
    ai_retry_backoff_seconds: float = 2.0
    # Optional per-model price in USD per 1K input/output tokens.
    ai_model_pricing: dict[str, dict[str, float]] = {}
    ai_input_cost_per_1k_tokens: float = 0.0
    ai_output_cost_per_1k_tokens: float = 0.0

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")
