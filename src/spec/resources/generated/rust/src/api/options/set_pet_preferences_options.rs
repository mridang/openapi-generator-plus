use std::collections::HashMap;
use std::time::Duration;

/// Options for the set_pet_preferences operation.
#[derive(Debug, Clone)]
pub struct SetPetPreferencesOptions {
    pub nickname: String,
    pub tags: Option<Vec<String>>,
    pub note: Option<String>,
    pub renewal_date: Option<chrono::NaiveDate>,
    pub reminder: Option<crate::date_time::DateTime>,
    pub interval_days: Option<i32>,
    pub ratio: Option<f64>,
    pub urgent: Option<bool>,
    pub snooze: Option<chrono::TimeDelta>,
    /// Replaces the client's end-to-end timeout for this call. Zero is refused
    /// with `ConfigurationError::InvalidTimeout` before anything is sent.
    pub timeout: Option<Duration>,
    /// Replaces the client's retry budget for this call; `Some(0)` turns the
    /// retries off.
    pub max_retries: Option<u32>,
    /// Headers added to this call. They win over the client's default headers
    /// and the operation's header parameters; the authentication headers are
    /// applied after them.
    pub headers: HashMap<String, String>,
}

impl SetPetPreferencesOptions {
    /// Creates a new SetPetPreferencesOptions, requiring every required parameter up front.
    ///
    /// Required fields are taken as constructor arguments so they cannot be
    /// silently omitted; optional fields start as `None` and are populated via
    /// the chained setters below.
    pub fn new(nickname: String) -> Self {
        Self {
            nickname,
            tags: None,
            note: None,
            renewal_date: None,
            reminder: None,
            interval_days: None,
            ratio: None,
            urgent: None,
            snooze: None,
            timeout: None,
            max_retries: None,
            headers: HashMap::new(),
        }
    }

    /// Sets the tags field.
    pub fn tags(mut self, tags: Vec<String>) -> Self {
        self.tags = Some(tags);
        self
    }

    /// Sets the note field.
    pub fn note(mut self, note: String) -> Self {
        self.note = Some(note);
        self
    }

    /// Sets the renewal_date field.
    pub fn renewal_date(mut self, renewal_date: chrono::NaiveDate) -> Self {
        self.renewal_date = Some(renewal_date);
        self
    }

    /// Sets the reminder field.
    pub fn reminder(mut self, reminder: crate::date_time::DateTime) -> Self {
        self.reminder = Some(reminder);
        self
    }

    /// Sets the interval_days field.
    pub fn interval_days(mut self, interval_days: i32) -> Self {
        self.interval_days = Some(interval_days);
        self
    }

    /// Sets the ratio field.
    pub fn ratio(mut self, ratio: f64) -> Self {
        self.ratio = Some(ratio);
        self
    }

    /// Sets the urgent field.
    pub fn urgent(mut self, urgent: bool) -> Self {
        self.urgent = Some(urgent);
        self
    }

    /// Sets the snooze field.
    pub fn snooze(mut self, snooze: chrono::TimeDelta) -> Self {
        self.snooze = Some(snooze);
        self
    }

    /// Sets the end-to-end timeout of this call, replacing the client's.
    pub fn timeout(mut self, timeout: Duration) -> Self {
        self.timeout = Some(timeout);
        self
    }

    /// Sets the retry budget of this call, replacing the client's.
    pub fn max_retries(mut self, max_retries: u32) -> Self {
        self.max_retries = Some(max_retries);
        self
    }

    /// Adds one header to this call, replacing an earlier one of the same name.
    pub fn header(mut self, name: &str, value: &str) -> Self {
        self.headers.insert(name.to_string(), value.to_string());
        self
    }

    /// The per-call settings the transport layer applies.
    pub(crate) fn call_options(&self) -> crate::api::base_api::CallOptions {
        crate::api::base_api::CallOptions {
            timeout: self.timeout,
            max_retries: self.max_retries,
            headers: self.headers.clone(),
        }
    }
}
