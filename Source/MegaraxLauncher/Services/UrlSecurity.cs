namespace MegaraxLauncher.Services;

public static class UrlSecurity
{
    public static Uri RequireAbsoluteUrl(string url, bool requireHttps, string fieldName)
    {
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri))
        {
            throw new InvalidDataException($"{fieldName} must be an absolute URL.");
        }

        if (requireHttps && uri.Scheme != Uri.UriSchemeHttps)
        {
            throw new InvalidDataException($"{fieldName} must use HTTPS.");
        }

        if (uri.Scheme != Uri.UriSchemeHttps && uri.Scheme != Uri.UriSchemeHttp)
        {
            throw new InvalidDataException($"{fieldName} must use HTTP or HTTPS.");
        }

        return uri;
    }
}
