use std::collections::HashMap;
use std::sync::Arc;

use crate::api::base_api::BaseApi;
use crate::api::base_api::InvokeApiParams;
#[allow(unused_imports)]
use crate::api::options::*;
use crate::api_client::ApiClient;
use crate::api_result::ApiResult;
use crate::authenticator::Authenticator;
use crate::configuration::Configuration;
use crate::models::*;
use crate::object_serializer;
use crate::value_serializer;
use crate::value_serializer::SerializedValue;

/// StoreApi provides methods for the Store API group.
/// Access to Petstore orders
pub struct StoreApi {
    base: BaseApi,
}

impl StoreApi {
    /// Creates a new StoreApi instance.
    pub fn new(api_client: Arc<dyn ApiClient>, config: Configuration) -> Self {
        Self {
            base: BaseApi::new(api_client, config),
        }
    }

    /// Delete purchase order by ID
    pub fn delete_order(
        &self,
        order_id: i64,
    ) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let result = self.delete_order_with_http_info(order_id)?;
        let _ = result;
        Ok(())
    }

    /// Performs the delete_order operation and returns the full API result.
    pub fn delete_order_with_http_info(
        &self,
        order_id: i64,
    ) -> Result<ApiResult<()>, Box<dyn std::error::Error + Send + Sync>> {
        let mut path = "/store/order/{orderId}".to_string();
        path = path.replace("{orderId}", &urlencoding::encode(&format!("{}", order_id)));

        let mut query_params: Vec<(String, String)> = Vec::new();

        let mut header_params: HashMap<String, String> = HashMap::new();

        let request_body: Option<Vec<u8>> = None;

        let response = self.base.invoke_api(InvokeApiParams {
            method: "DELETE",
            path: &path,
            query_params,
            header_params,
            body: request_body,
            accepts: vec![],
            content_type: "application/json",
            return_type: "",
            auth: None,
        })?;

        Ok(ApiResult {
            status_code: response.status_code,
            data: (),
            raw_body: response.body,
            headers: response.headers,
        })
    }

    /// Returns pet inventories by status
    pub fn get_inventory(
        &self,
    ) -> Result<std::collections::HashMap<String, i32>, Box<dyn std::error::Error + Send + Sync>>
    {
        let result = self.get_inventory_with_http_info()?;
        Ok(result.data)
    }

    /// Performs the get_inventory operation and returns the full API result.
    pub fn get_inventory_with_http_info(
        &self,
    ) -> Result<
        ApiResult<std::collections::HashMap<String, i32>>,
        Box<dyn std::error::Error + Send + Sync>,
    > {
        let mut path = "/store/inventory".to_string();

        let mut query_params: Vec<(String, String)> = Vec::new();

        let mut header_params: HashMap<String, String> = HashMap::new();

        let request_body: Option<Vec<u8>> = None;

        let response = self.base.invoke_api(InvokeApiParams {
            method: "GET",
            path: &path,
            query_params,
            header_params,
            body: request_body,
            accepts: vec!["application/json"],
            content_type: "application/json",
            return_type: "std::collections::HashMap<String, i32>",
            auth: None,
        })?;

        let data: std::collections::HashMap<String, i32> = if !response.body.is_empty() {
            object_serializer::deserialize(response.body.as_bytes())?
        } else {
            return Err("empty response body".into());
        };

        Ok(ApiResult {
            status_code: response.status_code,
            data,
            raw_body: response.body,
            headers: response.headers,
        })
    }

    /// Find purchase order by ID
    pub fn get_order_by_id(
        &self,
        order_id: i64,
    ) -> Result<Order, Box<dyn std::error::Error + Send + Sync>> {
        let result = self.get_order_by_id_with_http_info(order_id)?;
        Ok(result.data)
    }

    /// Performs the get_order_by_id operation and returns the full API result.
    pub fn get_order_by_id_with_http_info(
        &self,
        order_id: i64,
    ) -> Result<ApiResult<Order>, Box<dyn std::error::Error + Send + Sync>> {
        let mut path = "/store/order/{orderId}".to_string();
        path = path.replace("{orderId}", &urlencoding::encode(&format!("{}", order_id)));

        let mut query_params: Vec<(String, String)> = Vec::new();

        let mut header_params: HashMap<String, String> = HashMap::new();

        let request_body: Option<Vec<u8>> = None;

        let response = self.base.invoke_api(InvokeApiParams {
            method: "GET",
            path: &path,
            query_params,
            header_params,
            body: request_body,
            accepts: vec!["application/json"],
            content_type: "application/json",
            return_type: "Order",
            auth: None,
        })?;

        let data: Order = if !response.body.is_empty() {
            object_serializer::deserialize(response.body.as_bytes())?
        } else {
            return Err("empty response body".into());
        };

        Ok(ApiResult {
            status_code: response.status_code,
            data,
            raw_body: response.body,
            headers: response.headers,
        })
    }

    /// Place an order for a pet
    pub fn place_order(
        &self,
        order: Option<Order>,
    ) -> Result<Order, Box<dyn std::error::Error + Send + Sync>> {
        let result = self.place_order_with_http_info(order)?;
        Ok(result.data)
    }

    /// Performs the place_order operation and returns the full API result.
    pub fn place_order_with_http_info(
        &self,
        order: Option<Order>,
    ) -> Result<ApiResult<Order>, Box<dyn std::error::Error + Send + Sync>> {
        let mut path = "/store/order".to_string();

        let mut query_params: Vec<(String, String)> = Vec::new();

        let mut header_params: HashMap<String, String> = HashMap::new();

        let request_body = Some(object_serializer::serialize(&order)?);

        let response = self.base.invoke_api(InvokeApiParams {
            method: "POST",
            path: &path,
            query_params,
            header_params,
            body: request_body,
            accepts: vec!["application/json"],
            content_type: "application/json",
            return_type: "Order",
            auth: None,
        })?;

        let data: Order = if !response.body.is_empty() {
            object_serializer::deserialize(response.body.as_bytes())?
        } else {
            return Err("empty response body".into());
        };

        Ok(ApiResult {
            status_code: response.status_code,
            data,
            raw_body: response.body,
            headers: response.headers,
        })
    }
}
