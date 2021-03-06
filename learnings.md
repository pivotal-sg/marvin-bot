## Learnings

- Request object to handle structural validations:
  - first pass at checking existence of key-value pairs
  - handles passing of data to interactions

- Response object:
  - cherry-picks data that it needs to respond depending on
    delivery mechanism

- Adapter object to provide a consistence interface to interaction:
  - provide necessary information to use case

- Argument parsing is throwing exceptions:
  - create a result data object to encapsulate success/failure
  - enables information on failure to be codified in this object
