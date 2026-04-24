export type GenericFormState<T> =
  | { tag: 'editing'; error?: string; inputs: T }
  | { tag: 'submitting'; inputs: T }
  | { tag: 'redirect' }

export type GenericFormAction<T> =
  | { type: 'edit'; name: keyof T; value: string }
  | { type: 'submit' }
  | { type: 'success' }
  | { type: 'error'; message: string }

function logUnexpectedAction<T>(state: GenericFormState<T>, action: GenericFormAction<T>) {
  console.warn(`Unexpected action '${action.type}' on state '${state.tag}'`)
}

export function genericFormReducer<T>(
  state: GenericFormState<T>,
  action: GenericFormAction<T>,
): GenericFormState<T> {
  switch (state.tag) {
    case 'editing':
      if (action.type === 'edit') {
        return { tag: 'editing', error: undefined, inputs: { ...state.inputs, [action.name]: action.value } }
      }
      if (action.type === 'submit') {
        return { tag: 'submitting', inputs: state.inputs }
      }
      return state

    case 'submitting':
      if (action.type === 'success') {
        return { tag: 'redirect' }
      }
      if (action.type === 'error') {
        return { tag: 'editing', error: action.message, inputs: state.inputs }
      }
      return state

    case 'redirect':
      logUnexpectedAction(state, action)
      return state
  }
}
