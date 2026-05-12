import { util } from '@aws-appsync/utils';

export function request(ctx) {
    const id = util.autoId();
    const createdAt = util.time.nowISO8601();

    const item = {
        id,
        userId: ctx.args.userId,
        createdAt,
        payLoad: ctx.args.payLoad
    };

    return {
        operation: 'PutItem',
        key: util.dynamodb.toMapValues({ id }),
        attributeValues: util.dynamodb.toMapValues(item)
    };
}

export function response(ctx) {
    return ctx.result;
}