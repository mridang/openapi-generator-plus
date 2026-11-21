/* Builds a client from the environment, calls one operation and closes the client. */
use petstore::Client;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
    let host = std::env::var("PETSTORE_BASE_URL")?;
    let token = std::env::var("PETSTORE_ACCESS_TOKEN")?;
    let client = Client::with_token(Some(&host), Some(&token), None)?;
    let result = client.store.get_defaults(None).await;
    client.close();
    let value = result?;
    println!("{value:?}");
    Ok(())
}
