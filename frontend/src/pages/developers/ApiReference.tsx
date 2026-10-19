import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import { Loading } from '../../ui/Loading';
import { codeSpans, endpoints, example, nullable, refName, resolve, scopes, success, typeName, type OpenApi, type Schema } from './openapi';
import styles from './developers.module.css';

export const SPEC_PATH = '/api/v1/openapi.json';

/**
 * «Довідник» of «Для розробників» (етап 22.4): every operation and every shape of the API, read
 * from the server's own OpenAPI description, so it is never older than the API. Loaded only when
 * the page is opened (a separate chunk), the description itself only then too.
 */
export default function ApiReference() {
    const spec = useQuery({ queryKey: ['openapi', 'v1'], queryFn: () => api<OpenApi>(SPEC_PATH), staleTime: 5 * 60_000 });
    if (spec.isPending) return <Loading shape="lines" />;
    if (spec.isError) {
        return (
            <p role="alert">
                Не вдалося завантажити опис: {spec.error.message} Його можна відкрити й напряму: <a href={SPEC_PATH}>{SPEC_PATH}</a>.
            </p>
        );
    }
    const schemas = Object.entries(spec.data.components?.schemas ?? {}).sort(([a], [b]) => a.localeCompare(b));
    return (
        <div className={styles.reference}>
            <h3>Адреси</h3>
            {endpoints(spec.data).map(({ method, path, operation }) => {
                const answer = success(operation);
                const parameters = operation.parameters ?? [];
                return (
                    <details key={`${method} ${path}`} className={styles.operation} id={operation.operationId}>
                        <summary>
                            <span className={styles.method}>{method.toUpperCase()}</span>
                            <code className={styles.path}>{path}</code>
                            {operation.summary && <span className={styles.summary}>{operation.summary}</span>}
                        </summary>
                        {operation.description && <p><Text text={operation.description} /></p>}
                        {scopes(operation).length > 0 && (
                            <p className={styles.where}>
                                Лише з токеном із правом {scopes(operation).map((scope, index) => (
                                    <span key={scope}>{index > 0 && ' чи '}<code>{scope}</code></span>
                                ))}.
                            </p>
                        )}
                        {parameters.length > 0 && (
                            <dl className={styles.fields} aria-label="Параметри">
                                {parameters.map((parameter) => (
                                    <div key={`${parameter.in} ${parameter.name}`}>
                                        <dt>
                                            <code>{parameter.name}</code>{' '}
                                            <span className={styles.type}>{typeName(parameter.schema)}</span>{' '}
                                            <span className={styles.where}>
                                                {parameter.in === 'path' ? 'в адресі' : parameter.in === 'query' ? 'у запиті' : parameter.in}
                                                {parameter.required ? ', обовʼязковий' : ''}
                                            </span>
                                        </dt>
                                        <dd>
                                            {parameter.description && <Text text={parameter.description} />}
                                            <Values schema={parameter.schema} />
                                        </dd>
                                    </div>
                                ))}
                            </dl>
                        )}
                        <p>
                            Відповідь: <TypeLink schema={answer} />
                            {operation.operationId && <> · <code>{operation.operationId}</code></>}
                        </p>
                        {answer && (
                            <pre className={styles.code} aria-label="Приклад відповіді">
                                <code>{JSON.stringify(example(spec.data, answer), null, 2)}</code>
                            </pre>
                        )}
                    </details>
                );
            })}
            <h3>Форми відповідей</h3>
            {schemas.map(([name, schema]) => (
                <details key={name} className={styles.operation} id={`schema-${name}`}>
                    <summary><code className={styles.path}>{name}</code></summary>
                    {schema.description && <p><Text text={schema.description} /></p>}
                    <Fields spec={spec.data} schema={schema} />
                </details>
            ))}
        </div>
    );
}

function Fields({ spec, schema }: { spec: OpenApi; schema: Schema }) {
    const required = new Set(schema.required ?? []);
    const fields = Object.entries(resolve(spec, schema)?.properties ?? {});
    if (!fields.length) return null;
    return (
        <dl className={styles.fields} aria-label="Поля">
            {fields.map(([name, field]) => (
                <div key={name}>
                    <dt>
                        <code>{name}</code> <TypeLink schema={field} />
                        {!required.has(name) && <span className={styles.where}> може не бути</span>}
                    </dt>
                    <dd>
                        {field.description && <Text text={field.description} />}
                        <Values schema={field} />
                    </dd>
                </div>
            ))}
        </dl>
    );
}

/** A type, its named shapes linked to their descriptions below. */
function TypeLink({ schema }: { schema: Schema | undefined }) {
    if (!schema) return <span className={styles.type}>—</span>;
    const target = schema.$ref ?? schema.items?.$ref;
    const name = typeName(schema);
    if (!target) return <span className={styles.type}>{name}</span>;
    return <a className={styles.type} href={`#schema-${refName(target)}`}>{name}{nullable(schema) && !name.endsWith('null') ? ' | null' : ''}</a>;
}

function Values({ schema }: { schema: Schema | undefined }) {
    const values = schema?.enum ?? schema?.items?.enum;
    const fallback = schema?.default;
    if (!values?.length && fallback === undefined) return null;
    return (
        <span className={styles.values}>
            {values?.length ? <> Можна: {values.map((value, index) => <span key={String(value)}>{index > 0 && ', '}<code>{String(value)}</code></span>)}.</> : null}
            {fallback !== undefined && <> Без нього — <code>{String(fallback)}</code>.</>}
        </span>
    );
}

/** Text with `code` marks. */
function Text({ text }: { text: string }) {
    return <>{codeSpans(text).map((piece, index) => (index % 2 ? <code key={index}>{piece}</code> : piece))}</>;
}
