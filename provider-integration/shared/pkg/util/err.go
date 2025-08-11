package util

func FindError(err ...error) error {
	for _, e := range err {
		if e != nil {
			return e
		}
	}

	return nil
}

func MergeError(err1, err2 error) error {
	if err1 != nil {
		return err1
	}
	if err2 != nil {
		return err2
	}
	return nil
}

func MergeHttpErr(err1, err2 *HttpError) *HttpError {
	if err1 != nil {
		return err1
	}
	if err2 != nil {
		return err2
	}
	return nil
}
