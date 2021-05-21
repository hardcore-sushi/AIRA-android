use std::convert::TryInto;
use uuid::Bytes;
use crate::print_error;

pub fn to_uuid_bytes(bytes: &[u8]) -> Option<Bytes> {
    match bytes.try_into() {
        Ok(uuid) => Some(uuid),
        Err(e) => {
            print_error!(e);
            None
        }
    }
}

#[macro_export]
macro_rules! print_error {
    ($arg:tt) => ({
        println!("[{}:{}] {}", file!(), line!(), $arg);
    });
    ($($arg:tt)*) => ({
        println!("[{}:{}] {}", file!(), line!(), format_args!($($arg)*));
    })
}
