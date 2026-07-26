declare module "*.svg" {
  const src: string;
  export default src;
}

declare module "*.module.css" {
  const classes: { readonly [key: string]: string };
  export default classes;
}

declare module "*.png?asset" {
  const src: string;
  export default src;
}

declare module "*.ico?asset" {
  const src: string;
  export default src;
}
