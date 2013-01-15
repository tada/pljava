PostgreSQL save-points are exposed using the standard _setSavepoint()_ and _releaseSavepoint()_ methods on the _java.sql.Connection_ interface. Two restrictions apply:
* A save-point must be rolled back or released in the function where it was set.
* A save-point must not outlive the function where it was set.