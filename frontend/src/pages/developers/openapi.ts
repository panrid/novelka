/**
 * Just enough of OpenAPI 3.1 to show Новелка's own description (`/api/v1/openapi.json`) as a
 * readable reference (етап 22.4): no third-party viewer, nothing loaded on other pages.
 */

export type Schema = {
    $ref?: string;
    type?: string | string[];
    format?: string;
    description?: string;
    example?: unknown;
    default?: unknown;
    enum?: unknown[];
    items?: Schema;
    properties?: Record<string, Schema>;
    required?: string[];
    nullable?: boolean;
};

export type Parameter = { name: string; in: string; required?: boolean; description?: string; example?: unknown; schema?: Schema };

export type Answer = {
    description?: string;
    content?: Record<string, { schema?: Schema }>;
    headers?: Record<string, { description?: string }>;
};

export type Operation = {
    operationId?: string;
    summary?: string;
    description?: string;
    tags?: string[];
    parameters?: Parameter[];
    responses?: Record<string, Answer>;
    /** Who may call it; absent — the description's default (without a token or with «read»). */
    security?: Record<string, string[]>[];
};

export type OpenApi = {
    openapi: string;
    info: { title: string; version: string; description?: string };
    paths?: Record<string, Record<string, Operation>>;
    components?: { schemas?: Record<string, Schema> };
};

export type Endpoint = { method: string; path: string; operation: Operation };

const METHODS = ['get', 'post', 'put', 'patch', 'delete'];

/** Every operation in the order of the description: by path, then by method. */
export function endpoints(spec: OpenApi): Endpoint[] {
    return Object.entries(spec.paths ?? {}).flatMap(([path, item]) => METHODS.flatMap((method) => {
        const operation = item[method];
        return operation ? [{ method, path, operation }] : [];
    }));
}

/** «#/components/schemas/Translation» → «Translation». */
export function refName(ref: string): string {
    return ref.slice(ref.lastIndexOf('/') + 1);
}

function types(schema: Schema): string[] {
    const declared = schema.type === undefined ? [] : Array.isArray(schema.type) ? schema.type : [schema.type];
    return schema.nullable && !declared.includes('null') ? [...declared, 'null'] : declared;
}

/** Whether the value may be null. */
export function nullable(schema: Schema): boolean {
    return types(schema).includes('null');
}

/** The type as TypeScript would write it: `string`, `number`, `Translation[]`, `string | null`. */
export function typeName(schema: Schema | undefined): string {
    if (!schema) return 'unknown';
    if (schema.$ref) return refName(schema.$ref);
    const named = types(schema).filter((type) => type !== 'null').map((type) => {
        switch (type) {
            case 'integer':
                return 'number';
            case 'array': {
                const item = typeName(schema.items);
                return item.includes(' ') ? `(${item})[]` : `${item}[]`;
            }
            case 'object':
                return 'object';
            default:
                return type;
        }
    });
    const base = named.length ? named.join(' | ') : 'unknown';
    return nullable(schema) ? `${base} | null` : base;
}

/** The schema named by a reference, or the schema itself. */
export function resolve(spec: OpenApi, schema: Schema | undefined): Schema | undefined {
    if (!schema?.$ref) return schema;
    return spec.components?.schemas?.[refName(schema.$ref)];
}

/**
 * An example of a value of the schema, put together from the description's own examples: what a
 * response looks like before anyone calls it. References nest at most a few levels deep.
 */
export function example(spec: OpenApi, schema: Schema | undefined, depth = 0): unknown {
    const resolved = resolve(spec, schema);
    if (!resolved || depth > 5) return null;
    if (resolved.example !== undefined && !resolved.properties) return resolved.example;
    const kinds = types(resolved);
    if (resolved.properties) {
        return Object.fromEntries(Object.entries(resolved.properties).map(([name, field]) => [name, example(spec, field, depth + 1)]));
    }
    if (kinds.includes('array')) {
        const item = example(spec, resolved.items, depth + 1);
        return item === null ? [] : [item];
    }
    if (resolved.enum?.length) return resolved.enum[0];
    if (resolved.default !== undefined) return resolved.default;
    if (kinds.includes('integer') || kinds.includes('number')) return 0;
    if (kinds.includes('boolean')) return false;
    if (kinds.includes('string')) return '';
    return null;
}

/** The success answer's schema: 200 (or the first 2xx) as JSON. */
export function success(operation: Operation): Schema | undefined {
    const code = Object.keys(operation.responses ?? {}).find((key) => key.startsWith('2'));
    const content = code ? operation.responses?.[code]?.content : undefined;
    return content?.['application/json']?.schema ?? Object.values(content ?? {})[0]?.schema;
}

/** The rights a token needs for the operation, when it names them itself. */
export function scopes(operation: Operation): string[] {
    return [...new Set((operation.security ?? []).flatMap((requirement) => Object.values(requirement).flat()))];
}

/** Splits text on `code` marks: odd pieces are code. */
export function codeSpans(text: string): string[] {
    return text.split('`');
}
